import { describe, expect, it, vi } from 'vitest';
import {
  submeterPedido,
  aguardarArquivoPronto,
  buscarArquivo,
  FalhaSubmissaoError,
  TempoEsperaExcedidoError,
  FalhaBuscaArquivoError,
  type ConfigClienteDataLicense,
} from './cliente-data-license.js';
import type { PedidoDataLicense } from './tipos.js';

const configBase: ConfigClienteDataLicense = {
  baseUrl: 'https://data-license.bloomberg.example/api/v1',
  httpConfig: { timeoutMs: 1000, maxRetries: 0, baseBackoffMs: 1 },
  maxWaitMs: 5000,
  pollIntervalMs: 10,
};

const pedidoBase: PedidoDataLicense = {
  instrumentos: ['USSW5 Curncy', 'USDBRL Curncy'],
  campos: ['PX_LAST'],
  referenceDate: '2026-08-21',
};

describe('cliente-data-license', () => {
  describe('submeterPedido', () => {
    it('a. fake fetch devolve idPedido: confirma retorno e chamada com method POST e body esperado', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ idPedido: 'abc123' }),
      } as unknown as Response);

      const resultado = await submeterPedido(pedidoBase, configBase, fakeFetch);

      expect(resultado).toEqual({ idPedido: 'abc123' });
      expect(fakeFetch).toHaveBeenCalledTimes(1);

      const [url, init] = fakeFetch.mock.calls[0] as [string, RequestInit];
      expect(url).toBe('https://data-license.bloomberg.example/api/v1/requests');
      expect(init.method).toBe('POST');
      expect(init.headers).toEqual({ 'content-type': 'application/json' });
      expect(JSON.parse(init.body as string)).toEqual(pedidoBase);
    });

    it('b. fake fetch que rejeita simula falha de rede: confirma FalhaSubmissaoError', async () => {
      const fakeFetch = vi.fn().mockRejectedValue(new Error('connection reset by peer'));

      await expect(submeterPedido(pedidoBase, configBase, fakeFetch)).rejects.toThrow(
        FalhaSubmissaoError,
      );
    });

    it('c. fake fetch com ok=false e status 500: confirma FalhaSubmissaoError', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 500,
      } as unknown as Response);

      await expect(submeterPedido(pedidoBase, configBase, fakeFetch)).rejects.toThrow(
        FalhaSubmissaoError,
      );
    });
  });

  describe('aguardarArquivoPronto', () => {
    it('d. fake fetch devolve PROCESSANDO duas vezes e depois PRONTO: confirma PRONTO e duas chamadas a esperarImpl', async () => {
      let chamadas = 0;
      const fakeFetch = vi.fn().mockImplementation(async () => {
        chamadas++;
        if (chamadas <= 2) {
          return {
            ok: true,
            status: 200,
            json: async () => ({ status: 'PROCESSANDO' }),
          } as unknown as Response;
        }
        return {
          ok: true,
          status: 200,
          json: async () => ({ status: 'PRONTO' }),
        } as unknown as Response;
      });
      const fakeEsperar = vi.fn().mockResolvedValue(undefined);

      const resultado = await aguardarArquivoPronto(
        'pedido-456',
        configBase,
        fakeFetch,
        fakeEsperar,
      );

      expect(resultado).toBe('PRONTO');
      expect(fakeEsperar).toHaveBeenCalledTimes(2);
      expect(fakeEsperar).toHaveBeenCalledWith(configBase.pollIntervalMs);
    });

    it('e. fake fetch sempre PROCESSANDO: lanca TempoEsperaExcedidoError nomeando idPedido e maxWaitMs', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 'PROCESSANDO' }),
      } as unknown as Response);

      let relogioSimulado = 1000;
      const dateSpy = vi.spyOn(Date, 'now').mockImplementation(() => relogioSimulado);
      const fakeEsperar = vi.fn().mockImplementation(async (ms: number) => {
        relogioSimulado += ms;
      });

      const configCurto: ConfigClienteDataLicense = {
        ...configBase,
        maxWaitMs: 10,
        pollIntervalMs: 5,
      };

      try {
        let erroCapturado: unknown;
        try {
          await aguardarArquivoPronto(
            'pedido-timeout',
            configCurto,
            fakeFetch,
            fakeEsperar,
          );
        } catch (erro) {
          erroCapturado = erro;
        }

        expect(erroCapturado).toBeInstanceOf(TempoEsperaExcedidoError);
        const erroTimeout = erroCapturado as TempoEsperaExcedidoError;
        expect(erroTimeout.idPedido).toBe('pedido-timeout');
        expect(erroTimeout.maxWaitMs).toBe(10);
        expect(erroTimeout.message).toContain('pedido-timeout');
        expect(erroTimeout.message).toContain('10');
      } finally {
        dateSpy.mockRestore();
      }
    });

    it('f. fake fetch devolve SEM_DADO: confirma retorno SEM_DADO', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 'SEM_DADO' }),
      } as unknown as Response);

      const resultado = await aguardarArquivoPronto('pedido-789', configBase, fakeFetch);
      expect(resultado).toBe('SEM_DADO');
    });

    it('g. fake fetch devolve ERRO com motivo: lanca Error contendo o motivo', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        json: async () => ({ status: 'ERRO', motivo: 'campo inválido' }),
      } as unknown as Response);

      await expect(
        aguardarArquivoPronto('pedido-erro', configBase, fakeFetch),
      ).rejects.toThrow('campo inválido');
    });
  });

  describe('buscarArquivo', () => {
    it('h. fake fetch devolve arrayBuffer com bytes: confirma Buffer com conteudo esperado', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        arrayBuffer: async () => new TextEncoder().encode('conteudo real').buffer,
      } as unknown as Response);

      const buffer = await buscarArquivo('pedido-ok', configBase, fakeFetch);
      expect(Buffer.isBuffer(buffer)).toBe(true);
      expect(buffer.toString('utf-8')).toBe('conteudo real');
    });

    it('i. fake fetch devolve arrayBuffer vazio (0 bytes): lanca FalhaBuscaArquivoError', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: true,
        status: 200,
        arrayBuffer: async () => new ArrayBuffer(0),
      } as unknown as Response);

      await expect(buscarArquivo('pedido-vazio', configBase, fakeFetch)).rejects.toThrow(
        FalhaBuscaArquivoError,
      );
    });

    it('j. fake fetch com ok=false e status 404: lanca FalhaBuscaArquivoError', async () => {
      const fakeFetch = vi.fn().mockResolvedValue({
        ok: false,
        status: 404,
        arrayBuffer: async () => new ArrayBuffer(0),
      } as unknown as Response);

      await expect(buscarArquivo('pedido-404', configBase, fakeFetch)).rejects.toThrow(
        FalhaBuscaArquivoError,
      );
    });
  });
});
