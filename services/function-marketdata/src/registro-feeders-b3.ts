import type { BlobUploader } from './blob-storage.js';
import { FeederB3ArquivoPesquisaPregao } from './feeders/b3-arquivo-pesquisa-pregao.js';
import { FeederB3CurvaReferencia } from './feeders/b3-curva-referencia.js';
import { FeederB3TaxaSwap } from './feeders/b3-taxa-swap.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

/**
 * Códigos de curva do TaxaSwap.txt cobertos nesta plataforma, por dataset —
 * mesma lista de `ParserConfig.DATASETS_TAXA_SWAP` no `curve-processor`
 * (openspec/changes/b3-additional-curves). PRE é só o oráculo de validação
 * cruzada (tarefa 5 do backlog); as demais são curvas importadas novas.
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
  const feederPrecos = new FeederB3ArquivoPesquisaPregao(enviar, {
    prefixoArquivo: 'PR',
    blobUploader,
  });
  const feederCadastro = new FeederB3ArquivoPesquisaPregao(enviar, {
    prefixoArquivo: 'IN',
    blobUploader,
  });
  const feederCurvaPre = new FeederB3CurvaReferencia(enviar, { codigoCurva: 'PRE' });
  const feederTaxaSwap = new FeederB3TaxaSwap(enviar, { blobUploader });

  registro.registrar('PR_DI1', feederPrecos);
  registro.registrar('BVBG.086', feederPrecos);
  registro.registrar('BVBG.028', feederCadastro);
  registro.registrar('B3_CURVA_PRE', feederCurvaPre);
  for (const dataset of DATASETS_TAXA_SWAP) {
    registro.registrar(dataset, feederTaxaSwap);
  }
}
