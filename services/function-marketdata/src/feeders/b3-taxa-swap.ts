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

const ENCODING_TAXA_SWAP = 'utf-8';
/**
 * Container/fonte usado na convenção de caminho de blob (openspec/changes/raw-file-blob-storage).
 * "b3" sozinho (2 caracteres) viola o mínimo de 3 caracteres exigido pelo Azure Blob Storage para
 * nome de container — confirmado ao vivo nesta sessão contra Azurite real (`RestError: The
 * specified resource name length is not within the permissible limits`), nunca pego antes porque
 * nenhum feeder B3 tinha rodado ponta a ponta contra um blob storage real até agora.
 */
const BLOB_CONTAINER_B3 = 'b3-raw';
const PREFIXO_ARQUIVO_TAXA_SWAP = 'TS';
/** Confirmado ao vivo nesta sessão — ver javadoc de {@link urlDownloadB3}: `.ex_`, não `.zip`/`.exe`. */
const EXTENSAO_ARQUIVO_TAXA_SWAP = 'ex_';

const HTTP_CONFIG_PADRAO: HttpClientConfig = {
  timeoutMs: 60_000,
  maxRetries: 3,
  baseBackoffMs: 1_000,
};

export interface ConfigFeederB3TaxaSwap {
  readonly httpConfig?: HttpClientConfig;
  readonly fetchImpl?: FetchLike;
  readonly blobUploader: BlobUploader;
}

/**
 * Feeder do arquivo "Mercado de Derivativos – Taxas de Mercado para Swaps" da
 * B3 (`TaxaSwap.txt`, endpoint `pesquisapregao/download?filelist=TS<AAMMDD>.ex_`
 * — mesma família de endpoint de {@link FeederB3ArquivoPesquisaPregao}, prefixo
 * "TS", mas extensão `.ex_` — **confirmado ao vivo nesta sessão**, contra
 * várias datas reais, que `.zip`/`.exe` sempre devolvem um ZIP vazio de 22
 * bytes para este prefixo, mesmo em dia de pregão real; só `.ex_` devolve o
 * conteúdo de verdade), fonte para as curvas DCL/PTX/INP/DPL e para o oráculo
 * de validação cruzada de PRE (openspec/changes/b3-additional-curves).
 * <p>
 * **Igual a PR/IN, o ZIP baixado aqui É duplamente aninhado** — achado real
 * desta sessão, corrigindo a suposição original (de que TS seria um caso
 * único-nível, diferente de PR/IN): o corpo HTTP é um ZIP externo com UMA
 * entrada nomeada `TS<AAMMDD>.ex_` (o próprio stub self-extracting, cabeçalho
 * MZ), cujo CONTEÚDO é, ele mesmo, outro ZIP com a entrada real `TaxaSwap.txt`
 * dentro. Confirmado baixando e inspecionando o arquivo real de produção
 * (2026-09-14) fora do código do projeto, com `adm-zip`/`unzip -l` puros:
 * desempacotar só uma vez entrega os primeiros ~600 bytes do stub SFX (banner
 * "PKSFX CLI for Windows..."), não o TXT — o `B3TaxaSwapParser` do lado do
 * `curve-processor` rejeitava isso como `PARSE_FAILED` (linha de 605
 * caracteres, não 72), sem nenhum log visível do lado do consumidor por causa
 * do mesmo comportamento silencioso do `DefaultErrorHandler` do Spring Kafka
 * já diagnosticado em `ProcessarEnvelopeIngestaoUseCase` — só apareceu ao
 * inspecionar a dead-letter topic diretamente. `docs/TS260914.exe` (fornecido
 * pelo usuário) já era o arquivo do NÍVEL INTERNO (pós-primeiro desempacote),
 * não o corpo bruto da resposta HTTP — por isso o teste unitário contra a
 * fixture recortada (que reproduz só o nível externo) não pegou isso; a
 * fixture foi ajustada para o formato duplo real.
 * <p>
 * O arquivo traz mais de 100 códigos de curva na mesma publicação — este
 * feeder não filtra nada, grava o arquivo inteiro em blob e publica **um
 * evento por chamada**, sob o `dataset` que o chamador pediu (`params.dataset`,
 * ex. `B3_TAXA_SWAP_DCL`). Quem decide, do lado do `curve-processor`, qual
 * código de curva extrair do arquivo é o {@code B3TaxaSwapParser} registrado
 * para aquele dataset — cada dataset alvo (`B3_TAXA_SWAP_PRE|DCL|PTX|INP|DPL`)
 * dispara uma aquisição independente, cada uma baixando e regravando o mesmo
 * arquivo no mesmo caminho de blob (`b3-raw/<data>/TaxaSwap.txt`, gravação
 * idempotente) — mantém o feeder simples e o contrato `Feeder`/`AcquisitionParams`
 * (1 dataset por chamada) intacto, sem precisar de um retorno com múltiplos
 * lotes.
 */
export class FeederB3TaxaSwap implements Feeder {
  private readonly httpConfig: HttpClientConfig;
  private readonly fetchImpl: FetchLike | undefined;

  constructor(
    private readonly enviar: EnviarMensagem,
    private readonly config: ConfigFeederB3TaxaSwap,
  ) {
    this.httpConfig = config.httpConfig ?? HTTP_CONFIG_PADRAO;
    this.fetchImpl = config.fetchImpl;
  }

  async acquire(params: AcquisitionParams): Promise<AcquisitionResult> {
    if (!ehDiaDePregao(params.referenceDate)) {
      return noData(`${params.referenceDate} não é dia de pregão B3`);
    }

    const url = urlDownloadB3(PREFIXO_ARQUIVO_TAXA_SWAP, params.referenceDate, EXTENSAO_ARQUIVO_TAXA_SWAP);

    let response: Response;
    try {
      response = await fetchComRetentativa(url, this.httpConfig, this.fetchImpl);
    } catch (erro) {
      if (erro instanceof TransportError) {
        return failed('falha de transporte ao adquirir TaxaSwap da B3', erro.message);
      }
      throw erro;
    }

    const classificacao = classificarRespostaFonte(response);
    if (classificacao.tipo === 'NOT_YET_PUBLISHED') {
      return noData(`arquivo TaxaSwap ainda não publicado (status ${response.status})`);
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
      // ZIP externo válido mas sem nenhuma entrada — sinal real de "ainda não
      // publicado" para esta família de endpoint (ver javadoc da classe irmã
      // FeederB3ArquivoPesquisaPregao).
      return noData('arquivo TaxaSwap ainda não publicado (ZIP vazio)');
    }

    // O corpo HTTP é um ZIP de um ZIP (ver javadoc da classe) — a entrada do
    // ZIP externo (o stub self-extracting, cabeçalho MZ) ainda não é o TXT,
    // é o ZIP real, com TaxaSwap.txt dentro.
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

    const conteudoTxt = entradaMaisRecente.dados;

    const loteId = calcularLoteId('B3', params.dataset, params.referenceDate, conteudoTxt);
    const producedAt = new Date().toISOString();
    const contentHash = `sha256:${createHash('sha256').update(conteudoTxt).digest('hex')}`;

    const caminhoBlob = montarCaminhoBlob(params.referenceDate, entradaMaisRecente.nome);
    await this.config.blobUploader.gravar(BLOB_CONTAINER_B3, caminhoBlob, conteudoTxt);

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
          payloadKind: 'READY_CURVE',
          loteId,
          sequencia: 1,
          totalBlocos: 1,
          payload: {
            sourceUrl: url,
            encoding: ENCODING_TAXA_SWAP,
            contentHash,
            sizeBytes: conteudoTxt.length,
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
