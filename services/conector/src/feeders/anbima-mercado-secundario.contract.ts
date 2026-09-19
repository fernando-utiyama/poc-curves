/**
 * Teste de contrato opcional contra a ANBIMA real (mesmo espírito da tarefa
 * 6.8 do backlog do feeder B3). Fora do build padrão — depende de rede real.
 * Roda só sob demanda: npm run test:contract
 */
import { describe, expect, it, vi } from 'vitest';
import { AzureBlobUploader } from '../blob-storage.js';
import {
  DATASET_ANBIMA_MERCADO_SECUNDARIO,
  FeederAnbimaMercadoSecundario,
} from './anbima-mercado-secundario.js';
import type { AcquisitionParams } from '../feeder.js';

const paramsBase: AcquisitionParams = {
  source: 'ANBIMA',
  dataset: DATASET_ANBIMA_MERCADO_SECUNDARIO,
  referenceDate: '2026-08-21',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a66',
};

/** Exige um Azurite real de pé (localhost:10000) — mesmo princípio do contrato B3. */
const AZURITE_CONNECTION_STRING_LOCAL =
  'DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;';

describe('contrato real: arquivo de mercado secundário da ANBIMA', () => {
  it('PUBLISHED: baixa e processa de verdade o ms260821.txt real (pregão de 2026-08-21), grava em blob real', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    const blobUploader = new AzureBlobUploader(AZURITE_CONNECTION_STRING_LOCAL);
    const feeder = new FeederAnbimaMercadoSecundario(enviar, { blobUploader });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBe(1);
    }
    expect(enviar).toHaveBeenCalledTimes(1);
    const valorPublicado = JSON.parse(enviar.mock.calls[0]?.[0].valor);
    expect(valorPublicado.payload.blobContainer).toBe('anbima');
    expect(valorPublicado.payload.blobPath).toBe('2026-08-21/ms260821.txt');
  }, 30_000);

  it('NO_DATA: uma data futura sem arquivo publicado responde 404 real', async () => {
    const enviar = vi.fn();
    const blobUploader = new AzureBlobUploader(AZURITE_CONNECTION_STRING_LOCAL);
    const feeder = new FeederAnbimaMercadoSecundario(enviar, { blobUploader });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2035-08-21' }); // segunda-feira, não feriado

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  }, 15_000);
});
