import type { Faixa } from './feeder.js';

const TOPICO_POR_FAIXA: Record<Faixa, string> = {
  ROTINA: 'marketdata.rotina.v1',
  PRIORITARIA: 'marketdata.prioritaria.v1',
  MASSA: 'marketdata.massa.v1',
};

/**
 * Resolve o tópico Kafka de ingestão a partir da faixa recebida no disparo.
 * Toda publicação precisa declarar a faixa explicitamente — não há faixa
 * padrão, porque isso esconderia disparo manual entrando na faixa errada.
 *
 * @throws Error se faixa for nula, indefinida, ou não reconhecida
 */
export function resolverTopicoIngestao(faixa: Faixa | undefined | null): string {
  if (!faixa) {
    throw new Error(
      'faixa não pode ser vazia — todo disparo de aquisição precisa declarar a faixa de ingestão',
    );
  }
  const topico = TOPICO_POR_FAIXA[faixa];
  if (!topico) {
    throw new Error(`faixa desconhecida: ${faixa}`);
  }
  return topico;
}
