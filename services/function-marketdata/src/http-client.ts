/** Assinatura mínima de um `fetch`, para permitir injeção de um fake nos testes. */
export type FetchLike = (url: string, init: { signal: AbortSignal }) => Promise<Response>;

export interface HttpClientConfig {
  readonly timeoutMs: number;
  readonly maxRetries: number;
  readonly baseBackoffMs: number;
}

/** Lançado quando todas as tentativas se esgotam por falha de transporte (nunca por status HTTP de erro). */
export class TransportError extends Error {
  constructor(message: string) {
    super(message);
    this.name = 'TransportError';
  }
}

function esperarReal(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/**
 * Executa uma requisição GET com timeout por tentativa e retentativa com
 * backoff exponencial + jitter, mas só para falha de transporte (rede ou
 * timeout) — uma resposta HTTP com status de erro é devolvida imediatamente,
 * sem retentar, porque quem decide o que fazer com um 4xx/5xx é quem chama.
 *
 * `fetchImpl`, `esperarImpl` e `randomImpl` são injetáveis para permitir
 * testes determinísticos e rápidos, sem rede real e sem esperar de verdade.
 */
export async function fetchComRetentativa(
  url: string,
  config: HttpClientConfig,
  fetchImpl: FetchLike = fetch,
  esperarImpl: (ms: number) => Promise<void> = esperarReal,
  randomImpl: () => number = Math.random,
): Promise<Response> {
  let ultimoErro: unknown;

  for (let tentativa = 0; tentativa <= config.maxRetries; tentativa++) {
    try {
      const signal = AbortSignal.timeout(config.timeoutMs);
      return await fetchImpl(url, { signal });
    } catch (erro) {
      ultimoErro = erro;
      if (tentativa === config.maxRetries) {
        break;
      }
      const backoffBase = config.baseBackoffMs * 2 ** tentativa;
      const jitter = randomImpl() * backoffBase;
      await esperarImpl(backoffBase + jitter);
    }
  }

  const mensagemErro = ultimoErro instanceof Error ? ultimoErro.message : String(ultimoErro);
  throw new TransportError(
    `falha de transporte após ${config.maxRetries + 1} tentativa(s) para ${url}: ${mensagemErro}`,
  );
}
