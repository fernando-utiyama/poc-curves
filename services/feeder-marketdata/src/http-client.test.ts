import { describe, expect, it, vi } from 'vitest';
import { fetchComRetentativa, type HttpClientConfig, TransportError } from './http-client.js';

describe('fetchComRetentativa', () => {
  const url = 'https://api.exemplo.com/marketdata';
  const configPadrao: HttpClientConfig = {
    timeoutMs: 5000,
    maxRetries: 3,
    baseBackoffMs: 100,
  };

  it('sucesso na primeira tentativa: retorna resposta 200, chama fetchImpl 1 vez e nunca chama esperarImpl', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
    const esperarImpl = vi.fn().mockResolvedValue(undefined);

    const response = await fetchComRetentativa(url, configPadrao, fetchImpl, esperarImpl);

    expect(response.status).toBe(200);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(esperarImpl).not.toHaveBeenCalled();
  });

  it('falha de transporte uma vez, depois sucesso: retenta, espera backoff base e retorna resposta 200', async () => {
    const fetchImpl = vi
      .fn()
      .mockRejectedValueOnce(new Error('ECONNRESET'))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));
    const esperarImpl = vi.fn().mockResolvedValue(undefined);
    const randomImpl = () => 0;

    const response = await fetchComRetentativa(
      url,
      configPadrao,
      fetchImpl,
      esperarImpl,
      randomImpl,
    );

    expect(response.status).toBe(200);
    expect(fetchImpl).toHaveBeenCalledTimes(2);
    expect(esperarImpl).toHaveBeenCalledTimes(1);
    expect(esperarImpl).toHaveBeenCalledWith(configPadrao.baseBackoffMs);
  });

  it('todas as tentativas falham: lança TransportError com mensagem do erro e número de tentativas esperado', async () => {
    const config: HttpClientConfig = {
      timeoutMs: 5000,
      maxRetries: 2,
      baseBackoffMs: 100,
    };
    const fetchImpl = vi.fn().mockRejectedValue(new Error('network fail'));
    const esperarImpl = vi.fn().mockResolvedValue(undefined);
    const randomImpl = () => 0;

    const promise = fetchComRetentativa(url, config, fetchImpl, esperarImpl, randomImpl);

    await expect(promise).rejects.toThrow(TransportError);
    await expect(promise).rejects.toThrow(/network fail/);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it('status de erro HTTP (ex: 500) não é considerado falha de transporte e é retornado de imediato sem retentativa', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));
    const esperarImpl = vi.fn().mockResolvedValue(undefined);

    const response = await fetchComRetentativa(url, configPadrao, fetchImpl, esperarImpl);

    expect(response.status).toBe(500);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(esperarImpl).not.toHaveBeenCalled();
  });

  it('progressão exponencial do backoff: dobra o tempo de espera a cada retentativa (100, 200, 400)', async () => {
    const config: HttpClientConfig = {
      timeoutMs: 5000,
      maxRetries: 3,
      baseBackoffMs: 100,
    };
    const fetchImpl = vi
      .fn()
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockRejectedValueOnce(new Error('fail'))
      .mockResolvedValueOnce(new Response(null, { status: 200 }));
    const esperarImpl = vi.fn().mockResolvedValue(undefined);
    const randomImpl = () => 0;

    const response = await fetchComRetentativa(url, config, fetchImpl, esperarImpl, randomImpl);

    expect(response.status).toBe(200);
    expect(esperarImpl).toHaveBeenCalledTimes(3);
    expect(esperarImpl).toHaveBeenNthCalledWith(1, 100);
    expect(esperarImpl).toHaveBeenNthCalledWith(2, 200);
    expect(esperarImpl).toHaveBeenNthCalledWith(3, 400);
  });
});
