import { createHash } from 'node:crypto';
import { ehDiaDePregao } from '../calendario.js';
import { classificarRespostaFonte } from '../classificacao-fonte.js';
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
import { dividirLinhasEmBlocos } from '../linhas.js';
import { montarRecords } from '../registros-payload.js';

const HTTP_CONFIG_PADRAO: HttpClientConfig = {
  timeoutMs: 30_000,
  maxRetries: 3,
  baseBackoffMs: 1_000,
};

export interface ConfigFeederB3CurvaReferencia {
  readonly codigoCurva: string;
  readonly httpConfig?: HttpClientConfig;
  readonly fetchImpl?: FetchLike;
  readonly tamanhoBloco?: number;
}

// Arquivo real medido (PRE, 2026-08-21) tem 274 linhas de dado - 300 mantém tudo num bloco só no caso comum, com folga
const TAMANHO_BLOCO_PADRAO_CURVA_REFERENCIA = 300;

const HEADERS_B3_CURVA_REFERENCIA = {
  'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/151.0.0.0 Safari/537.36',
  'Accept': 'application/json, text/plain, */*',
  'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
};

const fetchComHeaders: FetchLike = (url, init) =>
  fetch(url, { ...init, headers: HEADERS_B3_CURVA_REFERENCIA });

/**
 * Feeder da curva pronta de referência da B3.
 *
 * Endpoint diferente do `pesquisapregao/download`, corpo duplamente encapsulado
 * (Base64 de um CSV ISO-8859-1), e necessita de headers obrigatórios contra WAF.
 * O sinal real de "sem pregão" (confirmado ao vivo para 2026-08-22) é status 200
 * mas com corpo vazio.
 */
export class FeederB3CurvaReferencia implements Feeder {
  private readonly config: ConfigFeederB3CurvaReferencia;
  private readonly fetchImpl: FetchLike;
  private readonly tamanhoBloco: number;
  private readonly httpConfig: HttpClientConfig;

  constructor(
    private readonly enviar: EnviarMensagem,
    config: ConfigFeederB3CurvaReferencia,
  ) {
    this.config = config;
    this.httpConfig = config.httpConfig ?? HTTP_CONFIG_PADRAO;
    this.fetchImpl = config.fetchImpl ?? fetchComHeaders;
    this.tamanhoBloco = config.tamanhoBloco ?? TAMANHO_BLOCO_PADRAO_CURVA_REFERENCIA;
  }

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    if (!ehDiaDePregao(params.referenceDate)) {
      return noData(`${params.referenceDate} não é dia de pregão`);
    }

    const jsonPayload = JSON.stringify({ language: 'pt-br', date: params.referenceDate, id: this.config.codigoCurva });
    const base64Payload = Buffer.from(jsonPayload, 'utf-8').toString('base64');
    const url = `https://sistemaswebb3-derivativos.b3.com.br/referenceRatesProxy/Search/GetDownloadFile/${base64Payload}`;

    let response: Response;
    try {
      response = await fetchComRetentativa(url, this.httpConfig, this.fetchImpl);
    } catch (erro) {
      if (erro instanceof TransportError) {
        return failed('falha de transporte ao adquirir curva pronta da B3', erro.message);
      }
      throw erro;
    }

    const classificacao = classificarRespostaFonte(response);
    if (classificacao.tipo === 'NOT_YET_PUBLISHED') {
      return noData(`curva pronta ainda não publicada (status ${response.status})`);
    }
    if (classificacao.tipo === 'FONTE_INDISPONIVEL') {
      return failed('fonte B3 indisponível', classificacao.detalhe);
    }

    const conteudoBase64 = Buffer.from(await response.arrayBuffer());

    const naoVazio = verificarConteudoNaoVazio(conteudoBase64);
    if (!naoVazio.valido) {
      return noData('curva pronta ainda não publicada (corpo vazio)');
    }

    let conteudoDecodificado: Buffer;
    try {
      conteudoDecodificado = Buffer.from(conteudoBase64.toString('utf-8'), 'base64');
      if (conteudoDecodificado.length === 0) {
        throw new Error('conteúdo decodificado vazio');
      }
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('resposta da B3 não é Base64 válido', mensagem);
    }

    let blocos;
    try {
      blocos = dividirLinhasEmBlocos(conteudoDecodificado, this.tamanhoBloco, 1);
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('estrutura de arquivo inesperada', mensagem);
    }

    const loteId = calcularLoteId('B3', params.dataset, params.referenceDate, conteudoDecodificado);
    const producedAt = new Date().toISOString();
    const contentHash = `sha256:${createHash('sha256').update(conteudoDecodificado).digest('hex')}`;

    for (const bloco of blocos) {
      await publicarBloco(
        {
          envelope: {
            eventId: calcularEventId(loteId, bloco.sequencia),
            correlationId: params.correlationId,
            source: 'B3',
            dataset: params.dataset,
            referenceDate: params.referenceDate,
            producedAt,
            schemaVersion: '1.0',
            payloadKind: 'READY_CURVE',
            loteId,
            sequencia: bloco.sequencia,
            totalBlocos: bloco.totalBlocos,
            payload: {
              sourceUrl: url,
              encoding: 'iso-8859-1',
              contentHash,
              sizeBytes: conteudoDecodificado.length,
              records: montarRecords(bloco, 'iso-8859-1'),
            },
          },
          faixa: params.faixa,
        },
        this.enviar,
      );
    }

    return published(loteId, blocos.length);
  }
}
