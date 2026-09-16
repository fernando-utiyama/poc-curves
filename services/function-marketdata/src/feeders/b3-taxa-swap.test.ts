import AdmZip from 'adm-zip';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it, vi } from 'vitest';
import { FeederB3TaxaSwap } from './b3-taxa-swap.js';
import type { AcquisitionParams } from '../feeder.js';
import type { BlobUploader } from '../blob-storage.js';

function criarBlobUploaderFake(): BlobUploader & { gravar: ReturnType<typeof vi.fn> } {
  return { gravar: vi.fn().mockResolvedValue(undefined) };
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const conteudoFixtureTaxaSwap = readFileSync(
  join(__dirname, '..', '..', 'fixtures', 'TaxaSwap_20260914_fixture.txt'),
);

const HTTP_CONFIG_RAPIDO = { timeoutMs: 1000, maxRetries: 1, baseBackoffMs: 1 };

function construirZipComEntradas(
  entradas: ReadonlyArray<{ nome: string; conteudo: Buffer; data: Date }>,
): Buffer {
  const zip = new AdmZip();
  for (const { nome, conteudo, data } of entradas) {
    const entrada = zip.addFile(nome, conteudo);
    entrada.header.time = data;
  }
  return zip.toBuffer();
}

/**
 * Reproduz a forma REAL do endpoint `pesquisapregao/download` para o prefixo
 * `TS` — **igual a PR/IN, duplamente aninhado** (achado real desta sessão,
 * ver javadoc de `FeederB3TaxaSwap`): um ZIP externo com UMA entrada nomeada
 * `TS<AAMMDD>.ex_` (o stub self-extracting), cujo conteúdo é outro ZIP com a
 * entrada real `TaxaSwap.txt` dentro.
 */
function construirRespostaTaxaSwap(nomeArquivoPedido: string, nomeEntradaInterna: string, conteudo: Buffer): Buffer {
  const zipInterno = construirZipComEntradas([{ nome: nomeEntradaInterna, conteudo, data: new Date() }]);
  return construirZipComEntradas([{ nome: nomeArquivoPedido, conteudo: zipInterno, data: new Date() }]);
}

function construirZipVazio(): Buffer {
  return new AdmZip().toBuffer();
}

const paramsBase: AcquisitionParams = {
  source: 'B3',
  dataset: 'B3_TAXA_SWAP_DCL',
  referenceDate: '2026-09-14',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
};

describe('FeederB3TaxaSwap', () => {
  it('PUBLISHED: desempacota os dois níveis de ZIP, grava o arquivo inteiro no blob e publica um evento sob o dataset pedido', async () => {
    const resposta = construirRespostaTaxaSwap('TS260914.ex_', 'TaxaSwap.txt', conteudoFixtureTaxaSwap);
    const fetchImpl = vi.fn().mockResolvedValue(new Response(resposta, { status: 200 }));
    const enviar = vi.fn().mockResolvedValue(undefined);
    const blobUploader = criarBlobUploaderFake();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    expect(fetchImpl).toHaveBeenCalledWith(
      'https://www.b3.com.br/pesquisapregao/download?filelist=TS260914.ex_',
      expect.anything(),
    );
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBe(1);
    }
    expect(enviar).toHaveBeenCalledTimes(1);
    expect(blobUploader.gravar).toHaveBeenCalledTimes(1);

    const [container, caminho, conteudoGravado] = blobUploader.gravar.mock.calls[0] as [
      string,
      string,
      Buffer,
    ];
    expect(container).toBe('b3-raw');
    expect(caminho).toBe('2026-09-14/TaxaSwap.txt');
    expect(conteudoGravado.equals(conteudoFixtureTaxaSwap)).toBe(true);

    const primeiraChamada = enviar.mock.calls[0]?.[0];
    const valorPublicado = JSON.parse(primeiraChamada.valor);
    expect(valorPublicado.dataset).toBe('B3_TAXA_SWAP_DCL');
    expect(valorPublicado.payload.blobContainer).toBe('b3-raw');
    expect(valorPublicado.payload.blobPath).toBe('2026-09-14/TaxaSwap.txt');
    expect(valorPublicado.payload.records).toBeUndefined();
  });

  it('PUBLISHED: mesma aquisição, dataset diferente (outra curva alvo) — o feeder não filtra nada, só muda o dataset publicado', async () => {
    const resposta = construirRespostaTaxaSwap('TS260914.ex_', 'TaxaSwap.txt', conteudoFixtureTaxaSwap);
    const fetchImpl = vi.fn().mockResolvedValue(new Response(resposta, { status: 200 }));
    const enviar = vi.fn().mockResolvedValue(undefined);

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    await feeder.acquire({ ...paramsBase, dataset: 'B3_TAXA_SWAP_INP' });

    const valorPublicado = JSON.parse(enviar.mock.calls[0]?.[0].valor);
    expect(valorPublicado.dataset).toBe('B3_TAXA_SWAP_INP');
  });

  it('NO_DATA: ZIP vazio (0 entradas) — sinal real de "ainda não publicado" para este endpoint', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(construirZipVazio(), { status: 200 }));
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('NO_DATA: dia sem pregão (feriado nacional) — nem chama a fonte HTTP', async () => {
    const fetchImpl = vi.fn();
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2026-09-07' }); // Independência

    expect(resultado.kind).toBe('NO_DATA');
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: falha de transporte esgota as tentativas', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('ECONNRESET'));
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: fonte indisponível (5xx real)', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 503 }));
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: conteúdo baixado não é um ZIP válido (corrompido)', async () => {
    const fetchImpl = vi
      .fn()
      .mockResolvedValue(new Response(Buffer.from('nao eh zip'), { status: 200 }));
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: ZIP externo válido, mas a entrada interna não é um ZIP (regressão real — era exatamente o formato assumido antes desta sessão corrigir para duplo aninhamento)', async () => {
    const respostaComEntradaInternaInvalida = construirZipComEntradas([
      { nome: 'TS260914.ex_', conteudo: conteudoFixtureTaxaSwap, data: new Date() },
    ]);
    const fetchImpl = vi
      .fn()
      .mockResolvedValue(new Response(respostaComEntradaInternaInvalida, { status: 200 }));
    const enviar = vi.fn();

    const feeder = new FeederB3TaxaSwap(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });
});
