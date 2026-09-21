import { createHash } from 'node:crypto';
import { ehDiaDePregao } from '../calendario.js';
import {
  failed,
  noData,
  published,
  type AcquisitionParams,
  type AcquisitionResult,
  type Feeder,
} from '../feeder.js';
import {
  fetchComRetentativa,
  TransportError,
  type FetchLike,
  type HttpClientConfig,
} from '../http-client.js';
import { verificarConteudoNaoVazio } from '../integridade.js';
import type { EnviarMensagem } from '../kafka-publisher.js';
import { publicarBloco } from '../kafka-publisher.js';
import { calcularEventId, calcularLoteId } from '../lote-id.js';
import { formatarDataDDMMAAAA } from '../data-ddmmaaaa.js';

export const DATASET_BCB_CDI = 'CDI';
export const DATASET_BCB_SELIC = 'SELIC';

const HTTP_CONFIG_PADRAO: HttpClientConfig = {
  timeoutMs: 30_000,
  maxRetries: 3,
  baseBackoffMs: 1_000,
};

export interface ConfigFeederBcbSerieTemporal {
  readonly httpConfig?: HttpClientConfig;
  readonly fetchImpl?: FetchLike;
}

interface CorpoDeErroBcb {
  readonly erro: {
    readonly statusCode?: number;
    readonly detail?: string;
  };
}

/** Reconhece o corpo `{"erro":{"statusCode":...}}` que o BCB devolve para ausência de dado ou falha, com status HTTP de transporte não confiável (ver descoberta crítica no Javadoc da classe). */
function ehCorpoDeErro(parsed: unknown): parsed is CorpoDeErroBcb {
  return (
    typeof parsed === 'object' &&
    parsed !== null &&
    !Array.isArray(parsed) &&
    'erro' in parsed &&
    typeof (parsed as { erro: unknown }).erro === 'object' &&
    (parsed as { erro: unknown }).erro !== null
  );
}

/**
 * Feeder para Séries Temporais do Banco Central do Brasil (SGS).
 * 
 * DESCOBERTA CRÍTICA: O status HTTP de transporte da API do BCB não é confiável.
 * O BCB pode retornar 404 real ou um 200 "mentiroso" com o mesmo corpo de erro
 * `{"erro":{"statusCode":404,...}}`. Por isso, a classificação (PUBLISHED vs NO_DATA
 * vs FAILED) é feita inspecionando o formato do corpo JSON baixado, e não com base
 * apenas em `response.status`.
 */
export class FeederBcbSerieTemporal implements Feeder {
  private readonly httpConfig: HttpClientConfig;
  private readonly fetchImpl: FetchLike | undefined;

  constructor(
    private readonly enviar: EnviarMensagem,
    private readonly codigoSerie: number,
    config: ConfigFeederBcbSerieTemporal = {},
  ) {
    this.httpConfig = config.httpConfig ?? HTTP_CONFIG_PADRAO;
    this.fetchImpl = config.fetchImpl;
  }

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    if (!ehDiaDePregao(params.referenceDate)) {
      return noData(`${params.referenceDate} não é dia de pregão`);
    }

    const data = formatarDataDDMMAAAA(params.referenceDate);
    const url = `https://api.bcb.gov.br/dados/serie/bcdata.sgs.${this.codigoSerie}/dados?formato=json&dataInicial=${data}&dataFinal=${data}`;

    let response: Response;
    try {
      response = await fetchComRetentativa(url, this.httpConfig, this.fetchImpl);
    } catch (erro) {
      if (erro instanceof TransportError) {
        return failed('falha de transporte ao adquirir dados do BCB', erro.message);
      }
      throw erro;
    }

    const conteudo = Buffer.from(await response.arrayBuffer());

    const naoVazio = verificarConteudoNaoVazio(conteudo);
    if (!naoVazio.valido) {
      return failed('download retornou conteúdo vazio', naoVazio.motivo);
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(conteudo.toString('utf-8'));
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('resposta do BCB não é JSON válido', mensagem);
    }

    if (Array.isArray(parsed)) {
      if (parsed.length === 0) {
        return noData(`BCB não publicou ${params.dataset} para ${params.referenceDate}`);
      }
      
      const loteId = calcularLoteId('BCB', params.dataset, params.referenceDate, conteudo);
      const producedAt = new Date().toISOString();
      const contentHash = `sha256:${createHash('sha256').update(conteudo).digest('hex')}`;
      
      const records = parsed.map((el) => ({ raw: JSON.stringify(el) }));
      
      const sequencia = 1;
      const totalBlocos = 1;
      
      const envelopeParams = {
        eventId: calcularEventId(loteId, sequencia),
        correlationId: params.correlationId,
        source: 'BCB' as const,
        dataset: params.dataset,
        referenceDate: params.referenceDate,
        producedAt,
        schemaVersion: '1.0',
        payloadKind: 'INDIVIDUAL_QUOTES' as const,
        loteId,
        sequencia,
        totalBlocos,
        payload: {
          sourceUrl: url,
          encoding: 'utf-8',
          contentHash,
          sizeBytes: conteudo.length,
          records
        }
      };

      await publicarBloco(
        { envelope: envelopeParams, faixa: params.faixa },
        this.enviar
      );

      return published(loteId, totalBlocos);
    }

    if (ehCorpoDeErro(parsed)) {
      const statusCode = parsed.erro.statusCode;
      if (statusCode === 404 || statusCode === undefined) {
        return noData(`BCB não publicou ${params.dataset} para ${params.referenceDate} (statusCode ${statusCode})`);
      }
      if (typeof statusCode === 'number' && statusCode >= 500) {
        return failed('fonte BCB indisponível', parsed.erro.detail ?? String(statusCode));
      }
      return failed('resposta inesperada do BCB', JSON.stringify(parsed.erro));
    }

    return failed('formato de resposta do BCB não reconhecido', conteudo.toString('utf-8').slice(0, 500));
  }
}
