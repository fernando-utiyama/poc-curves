import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { AcquisitionParams } from '../../feeder.js';
import type { EnviarMensagem } from '../../kafka-publisher.js';
import { FeederBloomberg, type ConfigFeederBloomberg } from './feeder-bloomberg.js';
import {
  submeterPedido,
  aguardarArquivoPronto,
  buscarArquivo,
  FalhaSubmissaoError,
  TempoEsperaExcedidoError,
  FalhaBuscaArquivoError,
} from './cliente-data-license.js';

vi.mock('./cliente-data-license.js', async (importOriginal) => {
  const actual = await importOriginal<typeof import('./cliente-data-license.js')>();
  return {
    ...actual,
    submeterPedido: vi.fn(),
    aguardarArquivoPronto: vi.fn(),
    buscarArquivo: vi.fn(),
  };
});

const configBase: ConfigFeederBloomberg = {
  clienteConfig: {
    baseUrl: 'https://data-license.bloomberg.example/api/v1',
    httpConfig: { timeoutMs: 1000, maxRetries: 0, baseBackoffMs: 1 },
    maxWaitMs: 5000,
    pollIntervalMs: 10,
  },
  instrumentos: ['USSW5 Curncy', 'USDBRL Curncy'],
  campos: ['PX_LAST'],
};

const paramsBase: AcquisitionParams = {
  source: 'BLOOMBERG',
  dataset: 'BLOOMBERG_JUROS_CAMBIO',
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
};

describe('FeederBloomberg', () => {
  let enviar: ReturnType<typeof vi.fn>;
  let feeder: FeederBloomberg;

  beforeEach(() => {
    vi.clearAllMocks();
    enviar = vi.fn().mockResolvedValue(undefined);
    feeder = new FeederBloomberg(enviar as unknown as EnviarMensagem, configBase);
  });

  it('a. fluxo feliz completo: submete, aguarda pronto, busca arquivo, divide em blocos e publica records raw como JSON de RegistroInstrumentoBruto', async () => {
    const csvConteudo = [
      'JUROS;SWAP;USSW5 Curncy;PX_LAST;4.523;2026-08-21',
      'CAMBIO;SPOT;USDBRL Curncy;PX_LAST;5.4231;2026-08-21',
    ].join('\n');
    const buffer = Buffer.from(csvConteudo, 'utf-8');

    vi.mocked(submeterPedido).mockResolvedValue({ idPedido: 'pedido-123' });
    vi.mocked(aguardarArquivoPronto).mockResolvedValue('PRONTO');
    vi.mocked(buscarArquivo).mockResolvedValue(buffer);

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBeGreaterThanOrEqual(1);
    }
    expect(enviar).toHaveBeenCalled();

    const chamada = enviar.mock.calls[0]?.[0];
    expect(chamada).toBeDefined();
    const envelopePublicado = JSON.parse(chamada.valor);
    expect(envelopePublicado.source).toBe('BLOOMBERG');
    expect(envelopePublicado.dataset).toBe('BLOOMBERG_JUROS_CAMBIO');
    expect(envelopePublicado.payload.records).toBeInstanceOf(Array);
    expect(envelopePublicado.payload.records.length).toBe(2);

    for (const record of envelopePublicado.payload.records) {
      expect(record).toHaveProperty('raw');
      expect(typeof record.raw).toBe('string');
      const parsedRecord = JSON.parse(record.raw);
      expect(parsedRecord).toHaveProperty('classeAtivo');
      expect(parsedRecord).toHaveProperty('tipoInstrumento');
      expect(parsedRecord).toHaveProperty('ticker');
      expect(parsedRecord).toHaveProperty('campo');
      expect(parsedRecord).toHaveProperty('valor');
      expect(parsedRecord).toHaveProperty('dataReferencia');
    }
  });

  it('b. submeterPedido lanca FalhaSubmissaoError: devolve FAILED e enviar NUNCA e chamado', async () => {
    vi.mocked(submeterPedido).mockRejectedValue(
      new FalhaSubmissaoError('falha de rede ao conectar na Bloomberg'),
    );

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    if (resultado.kind === 'FAILED') {
      expect(resultado.motivo).toBe('falha ao submeter pedido à Bloomberg');
      expect(resultado.diagnostico).toContain('falha de rede ao conectar na Bloomberg');
    }
    expect(enviar).not.toHaveBeenCalled();
  });

  it('c. aguardarArquivoPronto lanca TempoEsperaExcedidoError: devolve FAILED com mensagem contendo idPedido e enviar NUNCA e chamado', async () => {
    vi.mocked(submeterPedido).mockResolvedValue({ idPedido: 'pedido-timeout-xyz' });
    vi.mocked(aguardarArquivoPronto).mockRejectedValue(
      new TempoEsperaExcedidoError('pedido-timeout-xyz', 5000),
    );

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    if (resultado.kind === 'FAILED') {
      expect(resultado.motivo).toContain('pedido-timeout-xyz');
    }
    expect(enviar).not.toHaveBeenCalled();
  });

  it('d. aguardarArquivoPronto devolve SEM_DADO: devolve NO_DATA e buscarArquivo/enviar NUNCA sao chamados', async () => {
    vi.mocked(submeterPedido).mockResolvedValue({ idPedido: 'pedido-sem-dado' });
    vi.mocked(aguardarArquivoPronto).mockResolvedValue('SEM_DADO');

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('NO_DATA');
    expect(buscarArquivo).not.toHaveBeenCalled();
    expect(enviar).not.toHaveBeenCalled();
  });

  it('e. buscarArquivo lanca FalhaBuscaArquivoError: devolve FAILED e enviar NUNCA e chamado', async () => {
    vi.mocked(submeterPedido).mockResolvedValue({ idPedido: 'pedido-busca-erro' });
    vi.mocked(aguardarArquivoPronto).mockResolvedValue('PRONTO');
    vi.mocked(buscarArquivo).mockRejectedValue(
      new FalhaBuscaArquivoError('Bloomberg devolveu status 500 ao buscar'),
    );

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    if (resultado.kind === 'FAILED') {
      expect(resultado.motivo).toContain('pedido-busca-erro');
      expect(resultado.diagnostico).toContain('status 500');
    }
    expect(enviar).not.toHaveBeenCalled();
  });

  it('f. arquivo buscado com conteudo malformado rejeitado pelo parser: devolve FAILED e enviar NUNCA e chamado', async () => {
    vi.mocked(submeterPedido).mockResolvedValue({ idPedido: 'pedido-parser-erro' });
    vi.mocked(aguardarArquivoPronto).mockResolvedValue('PRONTO');
    vi.mocked(buscarArquivo).mockResolvedValue(Buffer.from('apenas;tres;campos', 'utf-8'));

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    if (resultado.kind === 'FAILED') {
      expect(resultado.motivo).toBe('estrutura de arquivo Bloomberg inesperada');
    }
    expect(enviar).not.toHaveBeenCalled();
  });
});
