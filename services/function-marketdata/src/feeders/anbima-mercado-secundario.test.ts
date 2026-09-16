import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it, vi } from 'vitest';
import {
  DATASET_ANBIMA_MERCADO_SECUNDARIO,
  FeederAnbimaMercadoSecundario,
} from './anbima-mercado-secundario.js';
import type { AcquisitionParams } from '../feeder.js';
import type { BlobUploader } from '../blob-storage.js';

function criarBlobUploaderFake(): BlobUploader & { gravar: ReturnType<typeof vi.fn> } {
  return { gravar: vi.fn().mockResolvedValue(undefined) };
}

const __dirname = dirname(fileURLToPath(import.meta.url));
const conteudoFixtureReal = readFileSync(
  join(__dirname, '..', '..', 'fixtures', 'ms260821_fixture.txt'),
);

const HTTP_CONFIG_RAPIDO = { timeoutMs: 1000, maxRetries: 1, baseBackoffMs: 1 };

const paramsBase: AcquisitionParams = {
  source: 'ANBIMA',
  dataset: DATASET_ANBIMA_MERCADO_SECUNDARIO,
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
};

function respostaComContentLength(conteudo: Buffer, status: number): Response {
  return new Response(conteudo, {
    status,
    headers: { 'content-length': String(conteudo.length) },
  });
}

describe('FeederAnbimaMercadoSecundario', () => {
  it('PUBLISHED: baixa o arquivo real (@-delimitado, ISO-8859-1, CRLF) e grava em blob storage', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(respostaComContentLength(conteudoFixtureReal, 200));
    const enviar = vi.fn().mockResolvedValue(undefined);
    const blobUploader = criarBlobUploaderFake();

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader,
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    expect(fetchImpl).toHaveBeenCalledWith(
      'https://www.anbima.com.br/informacoes/merc-sec/arqs/ms260821.txt',
      expect.anything(),
    );
    expect(enviar).toHaveBeenCalledTimes(1);
    expect(blobUploader.gravar).toHaveBeenCalledTimes(1);

    const [container, caminho, conteudoGravado] = blobUploader.gravar.mock.calls[0] as [
      string,
      string,
      Buffer,
    ];
    expect(container).toBe('anbima');
    expect(caminho).toBe('2026-08-21/ms260821.txt');
    // Conteúdo bruto completo (com cabeçalho institucional) gravado sem interpretação.
    expect(conteudoGravado.toString('latin1')).toContain('ANBIMA - Associação');
    expect(conteudoGravado.toString('latin1')).toContain('LTN@20260821');

    const primeiraChamada = enviar.mock.calls[0]?.[0];
    const valorPublicado = JSON.parse(primeiraChamada.valor);
    expect(valorPublicado.source).toBe('ANBIMA');
    expect(valorPublicado.payload.encoding).toBe('iso-8859-1');
    expect(valorPublicado.payload.blobContainer).toBe('anbima');
    expect(valorPublicado.payload.blobPath).toBe('2026-08-21/ms260821.txt');
    expect(valorPublicado.payload.records).toBeUndefined();
  });

  it('decodifica acentuação real corretamente (ISO-8859-1, não UTF-8) no conteúdo gravado no blob', async () => {
    // Uma linha de dado real fabricada com um campo de texto livre acentuado em ISO-8859-1.
    const cabecalho = Buffer.from('ANBIMA - Titulo\r\n\r\nTitulo@Desc\r\n', 'latin1');
    const linhaComAcento = Buffer.from('LTN@Título com acentuação\r\n', 'latin1');
    const conteudo = Buffer.concat([cabecalho, linhaComAcento]);

    const fetchImpl = vi.fn().mockResolvedValue(respostaComContentLength(conteudo, 200));
    const enviar = vi.fn().mockResolvedValue(undefined);
    const blobUploader = criarBlobUploaderFake();
    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader,
    });

    await feeder.acquire(paramsBase);

    const [, , conteudoGravado] = blobUploader.gravar.mock.calls[0] as [string, string, Buffer];
    expect(conteudoGravado.toString('latin1')).toContain('LTN@Título com acentuação');
  });

  it('NO_DATA: HTTP 404 real (ANBIMA responde 404 de verdade para arquivo inexistente, diferente da B3)', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(new Response('não encontrado', { status: 404 }));
    const enviar = vi.fn();

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('NO_DATA: dia sem pregão — nem chama a fonte HTTP', async () => {
    const fetchImpl = vi.fn();
    const enviar = vi.fn();

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2026-08-23' }); // domingo

    expect(resultado.kind).toBe('NO_DATA');
    expect(fetchImpl).not.toHaveBeenCalled();
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: falha de transporte esgota as tentativas', async () => {
    const fetchImpl = vi.fn().mockRejectedValue(new Error('ECONNRESET'));
    const enviar = vi.fn();

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
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

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });

  it('FAILED: tamanho do download diverge do Content-Length declarado', async () => {
    const fetchImpl = vi.fn().mockResolvedValue(
      new Response(conteudoFixtureReal, {
        status: 200,
        headers: { 'content-length': String(conteudoFixtureReal.length + 1000) },
      }),
    );
    const enviar = vi.fn();

    const feeder = new FeederAnbimaMercadoSecundario(enviar, {
      httpConfig: HTTP_CONFIG_RAPIDO,
      fetchImpl,
      blobUploader: criarBlobUploaderFake(),
    });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('FAILED');
    expect(enviar).not.toHaveBeenCalled();
  });
});
