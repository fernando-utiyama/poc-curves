import type { BlobUploader } from './blob-storage.js';
import { registrarFeedersAnbima } from './registro-feeders-anbima.js';
import { registrarFeedersB3 } from './registro-feeders-b3.js';
import { registrarFeedersBcb } from './registro-feeders-bcb.js';
import type { EnviarMensagem } from './kafka-publisher.js';
import { RegistroFeeders } from './registro-feeders.js';

/**
 * Monta o `RegistroFeeders` com todos os datasets de todas as fontes
 * implementadas (B3 e ANBIMA e BCB) — o composition root real usado pelos
 * adaptadores de execução. Acrescentar uma fonte nova (Bloomberg, LSEG)
 * significa escrever um `registrar-feeders-<fonte>.ts` no mesmo formato e
 * chamá-lo aqui — nenhum adaptador precisa mudar (docs/extensao-feeders.md).
 * `blobUploader` é usado pelos feeders de arquivo (B3, ANBIMA — ver
 * openspec/changes/raw-file-blob-storage); BCB não usa (não tem arquivo
 * original para arquivar).
 */
export function montarRegistroCompleto(
  enviar: EnviarMensagem,
  blobUploader: BlobUploader,
): RegistroFeeders {
  const registro = new RegistroFeeders();
  registrarFeedersB3(registro, enviar, blobUploader);
  registrarFeedersAnbima(registro, enviar, blobUploader);
  registrarFeedersBcb(registro, enviar);
  return registro;
}
