import { describe, expect, it, vi } from 'vitest';
import type { BlobUploader } from './blob-storage.js';
import { montarRegistroCompleto } from './registro-feeders-completo.js';
import { DatasetNaoSuportadoError } from './registro-feeders.js';
import type { EnviarMensagem } from './kafka-publisher.js';

const blobUploaderFake: BlobUploader = { gravar: vi.fn().mockResolvedValue(undefined) };

describe('montarRegistroCompleto', () => {
  it('não registra o dataset Bloomberg — só main.ts (CLI/agendado) faz isso, nunca o registro compartilhado por main-http.ts/azure-function-handler.ts', () => {
    const enviar: EnviarMensagem = vi.fn().mockResolvedValue(undefined);
    const registro = montarRegistroCompleto(enviar, blobUploaderFake);

    expect(() => registro.resolver('BLOOMBERG_JUROS_CAMBIO')).toThrow(DatasetNaoSuportadoError);
    expect(registro.datasetsRegistrados()).not.toContain('BLOOMBERG_JUROS_CAMBIO');
  });
});
