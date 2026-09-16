import { createHash } from 'node:crypto';
import { ehDiaDePregao } from '../calendario.js';
import { classificarRespostaFonte } from '../classificacao-fonte.js';
import type { BlobUploader } from '../blob-storage.js';
import { montarCaminhoBlob } from '../blob-storage.js';
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
import { lerEntradaMaisRecente, verificarArquivoZip } from '../zip.js';
import { urlDownloadB3 } from './nome-arquivo-b3.js';

const ENCODING_ARQUIVOS_B3 = 'utf-8';
/**
 * Container/fonte usado na convenção de caminho de blob (openspec/changes/raw-file-blob-storage).
 * "b3" sozinho (2 caracteres) viola o mínimo de 3 caracteres exigido pelo Azure Blob Storage para
 * nome de container — confirmado ao vivo contra Azurite real (`RestError: The specified resource
 * name length is not within the permissible limits`), achado ao rodar o feeder de TS ponta a
 * ponta pela primeira vez (openspec/changes/b3-additional-curves) — afetava também este feeder,
 * só nunca tinha sido exercitado contra um blob storage real até então.
 */
const BLOB_CONTAINER_B3 = 'b3-raw';

const HTTP_CONFIG_PADRAO: HttpClientConfig = {
  timeoutMs: 60_000,
  maxRetries: 3,
  baseBackoffMs: 1_000,
};

export interface ConfigFeederB3ArquivoPesquisaPregao {
  /** Prefixo do nome de arquivo no endpoint `pesquisapregao/download` — ex.: "PR", "IN". */
  readonly prefixoArquivo: string;
  readonly httpConfig?: HttpClientConfig;
  readonly fetchImpl?: FetchLike;
  readonly blobUploader: BlobUploader;
}

/**
 * Feeder para os datasets do endpoint `pesquisapregao/download` da B3
 * (`https://www.b3.com.br/pesquisapregao/download?filelist=<PREFIXO><AAMMDD>.zip`
 * — endpoint e padrão de nome confirmados por captura real de rede do
 * usuário em 2026-08-22). Cobre tanto "PR" (Preços de Referência, cujo
 * conteúdo real é BVBG.086 — confirmado nesta sessão que o arquivo interno é
 * `BVBG.086.01_*.xml`) quanto "IN" (cadastro de instrumentos, BVBG.028) —
 * mesma forma de aquisição para os dois, só muda o prefixo do arquivo.
 * <p>
 * Descobertas reais desta sessão que moldam este código (nenhuma é
 * suposição — todas verificadas baixando e inspecionando arquivos reais do
 * endpoint ao vivo e do pregão de 2026-08-21):
 * <ul>
 *   <li>O download é sempre um ZIP (nunca gzip) — mas **duas vezes
 *       aninhado**: o corpo HTTP é um ZIP externo com exatamente UMA entrada
 *       (nomeada igual ao arquivo pedido, ex. "PR260821.zip"), e o CONTEÚDO
 *       dessa entrada é, ele mesmo, outro ZIP — o real, com os XMLs dentro.
 *       Verificado ao vivo contra o endpoint real: uma primeira versão deste
 *       código desempacotava só uma vez e tratava os bytes do ZIP interno
 *       como se já fossem o XML, falhando com "nenhum elemento BizGrp
 *       encontrado" — o teste de contrato (tarefa 6.8) pegou isso.</li>
 *   <li>O ZIP interno (após o segundo desempacotamento) contém MAIS DE UM
 *       arquivo — revisões intraday do mesmo pregão (`PR260821.zip` tinha 4
 *       XMLs, `IN260821.zip` tinha 2). Usa-se a entrada com a data de
 *       modificação mais recente ({@link lerEntradaMaisRecente}).</li>
 *   <li>O endpoint SEMPRE responde HTTP 200, mesmo para um arquivo que não
 *       existe — nesse caso o ZIP EXTERNO é válido, porém vazio (0 entradas,
 *       22 bytes). É esse o sinal real de "ainda não publicado", não um 404 —
 *       a classificação genérica por status HTTP de {@link classificarRespostaFonte}
 *       segue usada como camada adicional de defesa (5xx real continua
 *       classificado como fonte indisponível), mas o sinal primário e
 *       confirmado é o ZIP externo vazio.</li>
 *   <li>O arquivo XML real do BVBG.028 chega a ~800MB — o conteúdo é gravado
 *       em blob storage inteiramente como `Buffer` (openspec/changes/
 *       raw-file-blob-storage), nunca convertido para `string` do V8, que
 *       tem limite de comprimento bem abaixo disso.</li>
 * </ul>
 */
export class FeederB3ArquivoPesquisaPregao implements Feeder {
  private readonly httpConfig: HttpClientConfig;
  private readonly fetchImpl: FetchLike | undefined;

  constructor(
    private readonly enviar: EnviarMensagem,
    private readonly config: ConfigFeederB3ArquivoPesquisaPregao,
  ) {
    this.httpConfig = config.httpConfig ?? HTTP_CONFIG_PADRAO;
    this.fetchImpl = config.fetchImpl;
  }

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    if (!ehDiaDePregao(params.referenceDate)) {
      return noData(`${params.referenceDate} não é dia de pregão B3`);
    }

    const url = urlDownloadB3(this.config.prefixoArquivo, params.referenceDate);

    let response: Response;
    try {
      response = await fetchComRetentativa(url, this.httpConfig, this.fetchImpl);
    } catch (erro) {
      if (erro instanceof TransportError) {
        return failed(
          `falha de transporte ao adquirir ${this.config.prefixoArquivo} da B3`,
          erro.message,
        );
      }
      throw erro;
    }

    const classificacao = classificarRespostaFonte(response);
    if (classificacao.tipo === 'NOT_YET_PUBLISHED') {
      return noData(
        `arquivo ${this.config.prefixoArquivo} ainda não publicado (status ${response.status})`,
      );
    }
    if (classificacao.tipo === 'FONTE_INDISPONIVEL') {
      return failed('fonte B3 indisponível', classificacao.detalhe);
    }

    const conteudoZip = Buffer.from(await response.arrayBuffer());

    const naoVazio = verificarConteudoNaoVazio(conteudoZip);
    if (!naoVazio.valido) {
      return failed('download da B3 retornou conteúdo vazio', naoVazio.motivo);
    }

    const zipValido = verificarArquivoZip(conteudoZip);
    if (!zipValido.valido) {
      return failed('arquivo ZIP corrompido ou inválido', zipValido.motivo);
    }

    let zipInterno: { nome: string; dados: Buffer };
    try {
      zipInterno = lerEntradaMaisRecente(conteudoZip);
    } catch {
      // ZIP externo válido mas sem nenhuma entrada — sinal real e confirmado
      // de "arquivo ainda não publicado" para este endpoint (ver javadoc da classe).
      return noData(`arquivo ${this.config.prefixoArquivo} ainda não publicado (ZIP vazio)`);
    }

    // O corpo HTTP é um ZIP de um ZIP (ver javadoc da classe) — a entrada do
    // ZIP externo ainda não é o XML, é o ZIP real, com as revisões intraday.
    const zipValidoInterno = verificarArquivoZip(zipInterno.dados);
    if (!zipValidoInterno.valido) {
      return failed('ZIP interno corrompido ou inválido', zipValidoInterno.motivo);
    }

    let entradaMaisRecente: { nome: string; dados: Buffer };
    try {
      entradaMaisRecente = lerEntradaMaisRecente(zipInterno.dados);
    } catch (erro) {
      const mensagem = erro instanceof Error ? erro.message : String(erro);
      return failed('ZIP interno sem nenhuma entrada', mensagem);
    }

    const conteudoXml = entradaMaisRecente.dados;

    const loteId = calcularLoteId('B3', params.dataset, params.referenceDate, conteudoXml);
    const producedAt = new Date().toISOString();
    const contentHash = `sha256:${createHash('sha256').update(conteudoXml).digest('hex')}`;

    const caminhoBlob = montarCaminhoBlob(params.referenceDate, entradaMaisRecente.nome);
    await this.config.blobUploader.gravar(BLOB_CONTAINER_B3, caminhoBlob, conteudoXml);

    await publicarBloco(
      {
        envelope: {
          eventId: calcularEventId(loteId, 1),
          correlationId: params.correlationId,
          source: 'B3',
          dataset: params.dataset,
          referenceDate: params.referenceDate,
          producedAt,
          schemaVersion: '1.0',
          payloadKind: 'INDIVIDUAL_QUOTES',
          loteId,
          sequencia: 1,
          totalBlocos: 1,
          payload: {
            sourceUrl: url,
            encoding: ENCODING_ARQUIVOS_B3,
            contentHash,
            sizeBytes: conteudoXml.length,
            blobContainer: BLOB_CONTAINER_B3,
            blobPath: caminhoBlob,
          },
        },
        faixa: params.faixa,
      },
      this.enviar,
    );

    return published(loteId, 1);
  }
}
