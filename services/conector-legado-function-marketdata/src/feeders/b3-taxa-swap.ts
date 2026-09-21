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
import { desempacotarZipDuploB3 } from '../zip.js';
import { BLOB_CONTAINER_B3, urlDownloadB3 } from './nome-arquivo-b3.js';

const ENCODING_TAXA_SWAP = 'utf-8';
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
 * — mesma família de endpoint `pesquisapregao` usada por PR/IN quando este
 * projeto ainda os ingeria (decisão do usuário: este projeto não fará mais
 * ingestão do BVBG — PR/IN foram removidos, TS é a única curva desta
 * família ainda em uso), prefixo "TS", extensão `.ex_`, ZIP duplamente
 * aninhado — ver `fixtures/README.md` para a descoberta completa e a
 * evidência real por trás de cada detalhe do formato), fonte para as
 * curvas PRE/DCL/PTX/INP/DPL (openspec/changes/b3-additional-curves).
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

    // O corpo HTTP é um ZIP de um ZIP (ver javadoc da classe) — a entrada do
    // ZIP externo (o stub self-extracting, cabeçalho MZ) ainda não é o TXT,
    // é o ZIP real, com TaxaSwap.txt dentro.
    const desempacotado = desempacotarZipDuploB3(conteudoZip);
    switch (desempacotado.tipo) {
      case 'ZIP_EXTERNO_VAZIO':
        // Sinal real de "ainda não publicado" para esta família de endpoint.
        return noData('arquivo TaxaSwap ainda não publicado (ZIP vazio)');
      case 'INVALIDO':
        return failed(
          desempacotado.etapa === 'EXTERNO' ? 'arquivo ZIP corrompido ou inválido' : 'ZIP interno corrompido ou inválido',
          desempacotado.motivo,
        );
    }

    const conteudoTxt = desempacotado.dados;

    const loteId = calcularLoteId('B3', params.dataset, params.referenceDate, conteudoTxt);
    const producedAt = new Date().toISOString();
    const contentHash = `sha256:${createHash('sha256').update(conteudoTxt).digest('hex')}`;

    const caminhoBlob = montarCaminhoBlob(params.referenceDate, desempacotado.nome);
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
