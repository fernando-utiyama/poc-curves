import type { EventEnvelope } from './envelope.js';
import { criarEnvelope, type ParametrosCriarEnvelope } from './envelope.js';
import { resolverTopicoIngestao } from './faixa-topico.js';
import type { Faixa } from './feeder.js';
import { validarEnvelope } from './validador-envelope.js';

/** Assinatura mínima de uma função de envio real a um broker Kafka, injetada por quem monta o feeder. */
export type EnviarMensagem = (params: {
  readonly topico: string;
  readonly chave: string;
  readonly valor: string;
}) => Promise<void>;

export interface PublicarBlocoParams {
  readonly envelope: ParametrosCriarEnvelope;
  readonly faixa: Faixa;
}

/** Um log estruturado em JSON de uma publicação — usado tanto para log real quanto em teste. */
export interface RegistroPublicacao {
  readonly nivel: 'info';
  readonly mensagem: string;
  readonly correlationId: string;
  readonly dataset: string;
  readonly referenceDate: string;
  readonly topico: string;
  readonly loteId: string;
  readonly sequencia: number;
  readonly totalBlocos: number;
}

/**
 * Monta o envelope, resolve tópico e chave de partição a partir da faixa
 * recebida, publica via `enviar` (injetado — nenhum cliente Kafka real
 * conhecido aqui) e registra um log estruturado em JSON propagando o
 * correlationId. Retorna o envelope publicado e o registro de log emitido,
 * para quem chama poder inspecionar em teste.
 *
 * @throws o mesmo erro de `criarEnvelope` se os parâmetros do envelope forem inválidos,
 *         o mesmo erro de `resolverTopicoIngestao` se a faixa for inválida,
 *         ou `EnvelopeInvalidoError` se o envelope montado não for conforme
 *         ao schema do contrato (validado antes do envio — o produtor nunca
 *         publica um evento fora do contrato)
 */
export async function publicarBloco(
  params: PublicarBlocoParams,
  enviar: EnviarMensagem,
  registrarLog: (registro: RegistroPublicacao) => void = (registro) =>
    console.log(JSON.stringify(registro)),
): Promise<{ envelope: EventEnvelope; log: RegistroPublicacao }> {
  const envelope = criarEnvelope(params.envelope);
  validarEnvelope(envelope);
  const topico = resolverTopicoIngestao(params.faixa);
  const chave = `${envelope.source}|${envelope.dataset}|${envelope.referenceDate}`;

  await enviar({
    topico,
    chave,
    valor: JSON.stringify(envelope),
  });

  const log: RegistroPublicacao = {
    nivel: 'info',
    mensagem: 'bloco publicado',
    correlationId: envelope.correlationId,
    dataset: envelope.dataset,
    referenceDate: envelope.referenceDate,
    topico,
    loteId: envelope.loteId,
    sequencia: envelope.sequencia,
    totalBlocos: envelope.totalBlocos,
  };
  registrarLog(log);

  return { envelope, log };
}
