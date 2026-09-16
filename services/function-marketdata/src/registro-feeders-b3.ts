import type { BlobUploader } from './blob-storage.js';
import { FeederB3ArquivoPesquisaPregao } from './feeders/b3-arquivo-pesquisa-pregao.js';
import { FeederB3CurvaReferencia } from './feeders/b3-curva-referencia.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import type { RegistroFeeders } from './registro-feeders.js';

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

  registro.registrar('PR_DI1', feederPrecos);
  registro.registrar('BVBG.086', feederPrecos);
  registro.registrar('BVBG.028', feederCadastro);
  registro.registrar('B3_CURVA_PRE', feederCurvaPre);
}
