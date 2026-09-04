import AdmZip from 'adm-zip';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it, vi } from 'vitest';
import { FeederB3ArquivoPesquisaPregao } from './b3-arquivo-pesquisa-pregao.js';
import type { AcquisitionParams } from '../feeder.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const conteudoFixturePR = readFileSync(
  join(__dirname, '..', '..', 'fixtures', 'BVBG.086.01_fixture.xml'),
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
 * Reproduz a forma REAL da resposta do endpoint `pesquisapregao/download`
 * (confirmado ao vivo contra a B3 nesta sessão): um ZIP externo com UMA
 * entrada nomeada `nomeArquivoPedido`, cujo conteúdo é OUTRO ZIP — o real,
 * com as revisões intraday do dataset.
 */
function construirRespostaB3DuploZip(
  nomeArquivoPedido: string,
  revisoes: ReadonlyArray<{ nome: string; conteudo: Buffer; data: Date }>,
): Buffer {
  const zipInterno = construirZipComEntradas(revisoes);
  return construirZipComEntradas([
    { nome: nomeArquivoPedido, conteudo: zipInterno, data: new Date() },
  ]);
}

function construirZipVazio(): Buffer {
  return new AdmZip().toBuffer();
}

const paramsBase: AcquisitionParams = {
  source: 'B3',
  dataset: 'BVBG.086',
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33',
};

describe('FeederB3ArquivoPesquisaPregao', () => {
  it('PUBLISHED: desempacota os dois níveis de ZIP, escolhe a revisão intraday mais recente, divide em blocos e publica todos', async () => {
    const respostaDuploZip = construirRespostaB3DuploZip('PR260821.zip', [
      {
        nome: 'BVBG.086.01_rev1.xml',
        conteudo: Buffer.from('<Vazio/>'),
        data: new Date('2026-08-21T18:42:00Z'),
      },
      {
        nome: 'BVBG.086.01_rev2_final.xml',
        conteudo: conteudoFixturePR,
        data: new Date('2026-08-21T20:37:00Z'),
      },
    ]);
    const fetchImpl = vi.fn().mockResolvedValue(new Response(respostaDuploZip, { status: 200 }));
    const enviar = vi.fn().mockResolvedValue(undefined);

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    expect(fetchImpl).toHaveBeenCalledWith(
      'https://www.b3.com.br/pesquisapregao/download?filelist=PR260821.zip',
      expect.anything(),
    );
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBeGreaterThan(0);
    }
    expect(enviar).toHaveBeenCalled();

    const primeiraChamada = enviar.mock.calls[0]?.[0];
    const valorPublicado = JSON.parse(primeiraChamada.valor);
    expect(valorPublicado.payload.records).toBeInstanceOf(Array);
    expect(valorPublicado.payload.records[0]).toHaveProperty('raw');
    expect(valorPublicado.payload.records[0].raw).toContain('<BizGrp>');
    // Prova que a revisão ESCOLHIDA foi a mais recente (rev2), não a rev1 (vazia).
    expect(valorPublicado.payload.records[0].raw).toContain('TTENT');
  });

  it('NO_DATA: ZIP externo vazio (0 entradas) — o sinal real confirmado de "ainda não publicado" para este endpoint', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(construirZipVazio(), { status: 200 }));
    const enviar = vi.fn();

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('NO_DATA: dia sem pregão (feriado nacional) — nem chama a fonte HTTP', async () => {
    const fetchImpl = vi.fn();
    const enviar = vi.fn();

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2026-09-07' }); // Independência

    expect(resultado.kind).toBe('NO_DATA');
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: falha de transporte esgota as tentativas', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('ECONNRESET'));
    const enviar = vi.fn();

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: fonte indisponível (5xx real)', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response(null, { status: 503 }));
    const enviar = vi.fn();

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
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

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: ZIP externo válido, mas a entrada interna não é um ZIP (formato inesperado)', async () => {
    const respostaComEntradaInternaInvalida = construirZipComEntradas([
      { nome: 'PR260821.zip', conteudo: Buffer.from('isto não é um zip'), data: new Date() },
    ]);
    const fetchImpl = vi
      .fn()
      .mockResolvedValue(new Response(respostaComEntradaInternaInvalida, { status: 200 }));
    const enviar = vi.fn();

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'PR',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('usa o prefixo IN para o dataset de cadastro (BVBG.028), mesma forma de aquisição', async () => {
    const resposta = construirRespostaB3DuploZip('IN260821.zip', [
      { nome: 'BVBG.028.02.xml', conteudo: conteudoFixturePR, data: new Date() },
    ]);
    const fetchImpl = vi.fn().mockResolvedValue(new Response(resposta, { status: 200 }));
    const enviar = vi.fn().mockResolvedValue(undefined);

    const feeder = new FeederB3ArquivoPesquisaPregao(enviar, {
      prefixoArquivo: 'IN',
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
    });

    await feeder.acquire({ ...paramsBase, dataset: 'BVBG.028' });

    expect(fetchImpl).toHaveBeenCalledWith(
      'https://www.b3.com.br/pesquisapregao/download?filelist=IN260821.zip',
      expect.anything(),
    );
  });
});
