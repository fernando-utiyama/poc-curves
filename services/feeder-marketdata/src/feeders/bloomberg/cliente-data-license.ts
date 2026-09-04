import { fetchComRetentativa, type FetchLike, type HttpClientConfig } from '../../http-client.js';
import type { PedidoDataLicense } from './tipos.js';

/**
 * Cliente do fluxo Bloomberg Data License por arquivo — submissão de
 * pedido, espera de prontidão (polling) e busca do arquivo gerado. O
 * transporte HTTP exato (endpoints, formato de request/response) é
 * MODELADO a partir do padrão público conhecido do Data License (submeter →
 * aguardar → buscar), NÃO verificado contra um ambiente Bloomberg real —
 * sem credenciais disponíveis nesta sessão (ver design.md da mudança
 * feeder-bloomberg, D-3, e proposal.md). Quando houver acesso real, só este
 * arquivo precisa mudar — o resto do feeder (parser, orquestração) não
 * depende do transporte exato.
 */

export class FalhaSubmissaoError extends Error {
  constructor(motivo: string) {
    super(motivo);
    this.name = 'FalhaSubmissaoError';
  }
}

export class TempoEsperaExcedidoError extends Error {
  constructor(
    public readonly idPedido: string,
    public readonly maxWaitMs: number,
  ) {
    super(`tempo máximo de espera (${maxWaitMs}ms) excedido aguardando arquivo do pedido ${idPedido}`);
    this.name = 'TempoEsperaExcedidoError';
  }
}

export class FalhaBuscaArquivoError extends Error {
  constructor(motivo: string) {
    super(motivo);
    this.name = 'FalhaBuscaArquivoError';
  }
}

export interface ConfigClienteDataLicense {
  readonly baseUrl: string;
  readonly httpConfig: HttpClientConfig;
  /** Tempo máximo total de espera pela geração do arquivo, em ms. */
  readonly maxWaitMs: number;
  /** Intervalo entre checagens de status, em ms. */
  readonly pollIntervalMs: number;
}

/**
 * Submete o pedido via POST. Tentativa ÚNICA, sem retentativa automática
 * (diferente de fetchComRetentativa, que só faz GET) — retentar um POST de
 * submissão arriscaria criar pedido duplicado na Bloomberg de verdade.
 * @throws FalhaSubmissaoError se a requisição falhar (rede, timeout, ou status HTTP de erro)
 */
export async function submeterPedido(
  pedido: PedidoDataLicense,
  config: ConfigClienteDataLicense,
  fetchImpl: FetchLike | typeof fetch = fetch,
): Promise<{ idPedido: string }> {
  let response: Response;
  try {
    const signal = AbortSignal.timeout(config.httpConfig.timeoutMs);
    response = await (fetchImpl as (url: string, init?: RequestInit) => Promise<Response>)(
      `${config.baseUrl}/requests`,
      {
        method: 'POST',
        headers: { 'content-type': 'application/json' },
        body: JSON.stringify(pedido),
        signal,
      },
    );
  } catch (erro) {
    const mensagem = erro instanceof Error ? erro.message : String(erro);
    throw new FalhaSubmissaoError(`falha de transporte ao submeter pedido Bloomberg: ${mensagem}`);
  }
  if (!response.ok) {
    throw new FalhaSubmissaoError(`Bloomberg recusou o pedido: status ${response.status}`);
  }
  const corpo = (await response.json()) as { idPedido?: string };
  if (!corpo.idPedido) {
    throw new FalhaSubmissaoError('resposta de submissão sem idPedido');
  }
  return { idPedido: corpo.idPedido };
}

type StatusPedido = 'PROCESSANDO' | 'PRONTO' | 'SEM_DADO' | 'ERRO';

/**
 * Faz polling do status até PRONTO, SEM_DADO, ERRO, ou o tempo máximo
 * estourar. `esperarImpl` é injetável para teste determinístico (nunca
 * espera de verdade em teste).
 * @returns 'PRONTO' se o arquivo está disponível, 'SEM_DADO' se a fonte respondeu que não há dado
 * @throws TempoEsperaExcedidoError se o tempo máximo estourar
 * @throws Error se o status retornar ERRO, nomeando o motivo reportado
 */
export async function aguardarArquivoPronto(
  idPedido: string,
  config: ConfigClienteDataLicense,
  fetchImpl?: FetchLike,
  esperarImpl: (ms: number) => Promise<void> = (ms) => new Promise((r) => setTimeout(r, ms)),
): Promise<'PRONTO' | 'SEM_DADO'> {
  const inicio = Date.now();
  while (true) {
    const response = await fetchComRetentativa(
      `${config.baseUrl}/requests/${idPedido}/status`,
      config.httpConfig,
      fetchImpl,
    );
    const corpo = (await response.json()) as { status: StatusPedido; motivo?: string };

    if (corpo.status === 'PRONTO') return 'PRONTO';
    if (corpo.status === 'SEM_DADO') return 'SEM_DADO';
    if (corpo.status === 'ERRO') {
      throw new Error(`Bloomberg reportou erro no pedido ${idPedido}: ${corpo.motivo ?? 'sem motivo informado'}`);
    }

    if (Date.now() - inicio >= config.maxWaitMs) {
      throw new TempoEsperaExcedidoError(idPedido, config.maxWaitMs);
    }
    await esperarImpl(config.pollIntervalMs);
  }
}

/**
 * Busca o arquivo pronto. Só deve ser chamada depois de `aguardarArquivoPronto` devolver 'PRONTO'.
 * @throws FalhaBuscaArquivoError se a busca falhar ou o conteúdo vier vazio
 */
export async function buscarArquivo(
  idPedido: string,
  config: ConfigClienteDataLicense,
  fetchImpl?: FetchLike,
): Promise<Buffer> {
  let response: Response;
  try {
    response = await fetchComRetentativa(`${config.baseUrl}/requests/${idPedido}/arquivo`, config.httpConfig, fetchImpl);
  } catch (erro) {
    const mensagem = erro instanceof Error ? erro.message : String(erro);
    throw new FalhaBuscaArquivoError(`falha ao buscar arquivo do pedido ${idPedido}: ${mensagem}`);
  }
  if (!response.ok) {
    throw new FalhaBuscaArquivoError(`Bloomberg devolveu status ${response.status} ao buscar o arquivo do pedido ${idPedido}`);
  }
  const conteudo = Buffer.from(await response.arrayBuffer());
  if (conteudo.length === 0) {
    throw new FalhaBuscaArquivoError(`arquivo do pedido ${idPedido} veio vazio`);
  }
  return conteudo;
}
