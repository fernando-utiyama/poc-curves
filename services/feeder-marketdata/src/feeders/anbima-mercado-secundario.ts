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
import { verificarConteudoNaoVazio, verificarTamanhoDeclarado } from '../integridade.js';
import type { EnviarMensagem } from '../kafka-publisher.js';
import { publicarBloco } from '../kafka-publisher.js';
import { calcularEventId, calcularLoteId } from '../lote-id.js';
import { dividirLinhasEmBlocos } from '../linhas.js';
import { montarRecords } from '../registros-payload.js';
import { urlDownloadAnbima } from './nome-arquivo-anbima.js';

/** Nome de dataset sob o qual este feeder é registrado — ver `registro-feeders-anbima.ts`. */
export const DATASET_ANBIMA_MERCADO_SECUNDARIO = 'ANBIMA_MERCADO_SECUNDARIO';

const ENCODING_ARQUIVO_ANBIMA = 'iso-8859-1';
/** Título + linha em branco + cabeçalho — confirmado real contra `ms260821.txt` (fixtures/ms260821_fixture.txt). */
const LINHAS_DE_CABECALHO = 3;

const HTTP_CONFIG_PADRAO: HttpClientConfig = {
  timeoutMs: 30_000,
  maxRetries: 3,
  baseBackoffMs: 1_000,
};

export interface ConfigFeederAnbimaMercadoSecundario {
  readonly httpConfig?: HttpClientConfig;
  readonly fetchImpl?: FetchLike;
  readonly tamanhoBloco?: number;
}

const TAMANHO_BLOCO_PADRAO = 50;

/**
 * Feeder do arquivo de mercado secundário da ANBIMA
 * (`https://www.anbima.com.br/informacoes/merc-sec/arqs/ms<AAMMDD>.txt` —
 * endpoint e padrão de nome confirmados por captura real de rede do usuário
 * em 2026-08-22, e o conteúdo inspecionado ao vivo nesta sessão).
 * <p>
 * Descobertas reais desta sessão que moldam este código:
 * <ul>
 *   <li>Texto puro delimitado por `@`, ISO-8859-1, terminador CRLF — não é
 *       ZIP (diferente da B3). Confirmado com `curl` real: `Content-Type:
 *       text/plain`, `Content-Length` presente de verdade (ao contrário do
 *       endpoint da B3, que nunca declara tamanho).</li>
 *   <li>Três linhas de cabeçalho antes dos dados: título institucional,
 *       linha em branco, e a linha com os nomes das colunas — confirmado
 *       real (`fixtures/ms260821_fixture.txt`, arquivo real completo do
 *       pregão de 2026-08-21, só 6.812 bytes — pequeno o bastante para
 *       versionar por inteiro, ao contrário dos arquivos da B3).</li>
 *   <li>Ao contrário da B3, este endpoint responde HTTP 404 de verdade
 *       (página de erro real do IIS) para um arquivo que não existe — a
 *       classificação genérica por status HTTP de
 *       {@link classificarRespostaFonte} funciona sem ajuste nenhum aqui.</li>
 *   <li>Dados reais: taxas indicativas e PU de títulos públicos federais
 *       (LTN, LFT, NTN-B, NTN-C, NTN-F) — um ativo diferente da curva DI da
 *       B3, não um substituto para a tarefa 3.4 (curva pronta) do backlog do
 *       feeder B3.</li>
 * </ul>
 * Corte estrutural por LINHA (`dividirLinhasEmBlocos`), não por elemento
 * XML — cada linha de dado vira um registro opaco, sem interpretar campo
 * nenhum (mesmo princípio de xml-estrutural.ts para a B3).
 */
export class FeederAnbimaMercadoSecundario implements Feeder {
  private readonly httpConfig: HttpClientConfig;
  private readonly fetchImpl: FetchLike | undefined;
  private readonly tamanhoBloco: number;

  constructor(
    private readonly enviar: EnviarMensagem,
    config: ConfigFeederAnbimaMercadoSecundario = {},
  ) {
    this.httpConfig = config.httpConfig ?? HTTP_CONFIG_PADRAO;
    this.fetchImpl = config.fetchImpl;
    this.tamanhoBloco = config.tamanhoBloco ?? TAMANHO_BLOCO_PADRAO;
  }

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    if (!ehDiaDePregao(params.referenceDate)) {
      return noData(`${params.referenceDate} não é dia de pregão`);
    }

    const url = urlDownloadAnbima(params.referenceDate);

    let response: Response;
    try {
      response = await fetchComRetentativa(url, this.httpConfig, this.fetchImpl);
    } catch (erro) {
      if (erro instanceof TransportError) {
        return failed('falha de transporte ao adquirir mercado secundário da ANBIMA', erro.message);
      }
      throw erro;
    }

    const classificacao = classificarRespostaFonte(response);
    if (classificacao.tipo === 'NOT_YET_PUBLISHED') {
      return noData(`arquivo ANBIMA ainda não publicado (status ${response.status})`);
    }
    if (classificacao.tipo === 'FONTE_INDISPONIVEL') {
      return failed('fonte ANBIMA indisponível', classificacao.detalhe);
    }

    const conteudo = Buffer.from(await response.arrayBuffer());

    const naoVazio = verificarConteudoNaoVazio(conteudo);
    if (!naoVazio.valido) {
      return failed('download da ANBIMA retornou conteúdo vazio', naoVazio.motivo);
    }

    const tamanhoDeclarado = response.headers.get('content-length');
    if (tamanhoDeclarado !== null) {
      const tamanhoOk = verificarTamanhoDeclarado(conteudo, Number(tamanhoDeclarado));
      if (!tamanhoOk.valido) {
        return failed('tamanho do download divergente do declarado', tamanhoOk.motivo);
      }
    }

    let blocos;
    try {
      blocos = dividirLinhasEmBlocos(conteudo, this.tamanhoBloco, LINHAS_DE_CABECALHO);
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('estrutura de arquivo inesperada', mensagem);
    }

    const loteId = calcularLoteId('ANBIMA', params.dataset, params.referenceDate, conteudo);
    const producedAt = new Date().toISOString();
    const contentHash = `sha256:${createHash('sha256').update(conteudo).digest('hex')}`;

    for (const bloco of blocos) {
      await publicarBloco(
        {
          envelope: {
            eventId: calcularEventId(loteId, bloco.sequencia),
            correlationId: params.correlationId,
            source: 'ANBIMA',
            dataset: params.dataset,
            referenceDate: params.referenceDate,
            producedAt,
            schemaVersion: '1.0',
            payloadKind: 'INDIVIDUAL_QUOTES',
            loteId,
            sequencia: bloco.sequencia,
            totalBlocos: bloco.totalBlocos,
            payload: {
              sourceUrl: url,
              encoding: ENCODING_ARQUIVO_ANBIMA,
              contentHash,
              sizeBytes: conteudo.length,
              records: montarRecords(bloco, ENCODING_ARQUIVO_ANBIMA),
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
