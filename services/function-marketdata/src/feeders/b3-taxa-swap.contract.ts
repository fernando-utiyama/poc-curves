/**
 * Teste de contrato opcional contra a B3 real (tarefa 2.4 do backlog
 * openspec/changes/b3-additional-curves). Fora do build padrão de propósito
 * — depende de rede real e de um endpoint de terceiros fora do nosso
 * controle. Roda só sob demanda:
 *
 *   npm run test:contract
 *
 * (config dedicada `vitest.contract.config.ts`, incluindo apenas
 * `src/**\/*.contract.ts` — nunca coletado por `npm test`).
 */
import { describe, expect, it, vi } from 'vitest';
import { AzureBlobUploader } from '../blob-storage.js';
import { FeederB3TaxaSwap } from './b3-taxa-swap.js';
import type { AcquisitionParams } from '../feeder.js';

const paramsBase: AcquisitionParams = {
  source: 'B3',
  dataset: 'B3_TAXA_SWAP_PRE',
  referenceDate: '2026-09-14',
  faixa: 'ROTINA',
  correlationId: 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a55',
};

/**
 * Exige um Azurite real de pé (localhost:10000, mesma connection string de
 * desenvolvimento usada em deploy/podman/compose.core.yaml) — este teste,
 * como o resto do arquivo, só roda sob demanda via `npm run test:contract`,
 * nunca na suíte padrão.
 */
const AZURITE_CONNECTION_STRING_LOCAL =
  'DefaultEndpointsProtocol=http;AccountName=devstoreaccount1;AccountKey=Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==;BlobEndpoint=http://127.0.0.1:10000/devstoreaccount1;';

describe('contrato real: endpoint pesquisapregao da B3, prefixo TS (TaxaSwap.txt)', () => {
  it('PUBLISHED: baixa e processa de verdade o TS<data>.zip real, único nível de ZIP, grava em blob real', async () => {
    const enviar = vi.fn().mockResolvedValue(undefined);
    const blobUploader = new AzureBlobUploader(AZURITE_CONNECTION_STRING_LOCAL);
    const feeder = new FeederB3TaxaSwap(enviar, { blobUploader });

    const resultado = await feeder.acquire(paramsBase);

    expect(resultado.kind).toBe('PUBLISHED');
    if (resultado.kind === 'PUBLISHED') {
      expect(resultado.totalBlocos).toBe(1);
    }
    expect(enviar).toHaveBeenCalledTimes(1);
    const valorPublicado = JSON.parse(enviar.mock.calls[0]?.[0].valor);
    expect(valorPublicado.dataset).toBe('B3_TAXA_SWAP_PRE');
    expect(valorPublicado.payload.blobContainer).toBe('b3-raw');
    expect(valorPublicado.payload.blobPath).toMatch(/^2026-09-14\/TaxaSwap\.txt$/);
    // sizeBytes real do arquivo inteiro (106 códigos x 278 vértices) é bem maior que a fixture
    // recortada — confirma que o feeder gravou o arquivo de produção, não um teste acidental.
    expect(valorPublicado.payload.sizeBytes).toBeGreaterThan(1_000_000);
  }, 90_000);

  it('NO_DATA: uma data futura ainda sem arquivo publicado responde com ZIP vazio, não erro', async () => {
    const enviar = vi.fn();
    const blobUploader = new AzureBlobUploader(AZURITE_CONNECTION_STRING_LOCAL);
    const feeder = new FeederB3TaxaSwap(enviar, { blobUploader });

    const resultado = await feeder.acquire({ ...paramsBase, referenceDate: '2035-09-14' }); // Friday, not a holiday

    expect(resultado.kind).toBe('NO_DATA');
    expect(enviar).not.toHaveBeenCalled();
  }, 30_000);
});
