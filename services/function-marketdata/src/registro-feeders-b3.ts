import type { BlobUploader } from './blob-storage.js';
import { FeederB3CurvaReferencia } from './feeders/b3-curva-referencia.js';
import { FeederB3TaxaSwap } from './feeders/b3-taxa-swap.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

/**
 * Códigos de curva do TaxaSwap.txt cobertos nesta plataforma, por dataset
 * (openspec/changes/b3-additional-curves) — as 5 únicas curvas reais do
 * projeto (BVBG.086/BVBG.028/PR_DI1, a antiga curva DI1 BOOTSTRAPPED, não
 * são mais ingeridas: decisão do usuário, "este projeto não fará ingestão
 * do BVBG").
 */
const DATASETS_TAXA_SWAP = [
  'B3_TAXA_SWAP_PRE',
  'B3_TAXA_SWAP_DCL',
  'B3_TAXA_SWAP_PTX',
  'B3_TAXA_SWAP_INP',
  'B3_TAXA_SWAP_DPL',
];

/**
 * Registra os feeders B3 já implementados em um `RegistroFeeders` existente
 * — usado por todos os adaptadores de execução (`main.ts`,
 * `azure-function-handler.ts`), para nenhum deles ter regra de aquisição
 * própria (ver docs/extensao-feeders.md).
 */
export function registrarFeedersB3(
  registro: RegistroFeeders,
  enviar: EnviarMensagem,
  blobUploader: BlobUploader,
): void {
  const feederCurvaPre = new FeederB3CurvaReferencia(enviar, { codigoCurva: 'PRE' });
  const feederTaxaSwap = new FeederB3TaxaSwap(enviar, { blobUploader });

  registro.registrar('B3_CURVA_PRE', feederCurvaPre);
  for (const dataset of DATASETS_TAXA_SWAP) {
    registro.registrar(dataset, feederTaxaSwap);
  }
}
