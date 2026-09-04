import { describe, expect, it } from 'vitest';
import { TAMANHO_BLOCO_PADRAO } from './tamanho-bloco.js';

describe('TAMANHO_BLOCO_PADRAO', () => {
  const LIMITE_KAFKA_BYTES = 1_048_576; // message.max.bytes padrão, não sobrescrito no compose local
  const MARGEM_ENVELOPE_JSON = 0.7; // 30% reservado para escaping JSON + demais campos do envelope
  const BYTES_POR_ELEMENTO_PIOR_CASO = 3577; // medido em BVBG.028.02 real (223.700 elementos / 800.282.039 bytes)

  it('é um inteiro positivo', () => {
    expect(Number.isInteger(TAMANHO_BLOCO_PADRAO)).toBe(true);
    expect(TAMANHO_BLOCO_PADRAO).toBeGreaterThan(0);
  });

  it('mantém o bloco estimado dentro do limite de mensagem do Kafka local, mesmo no pior caso medido', () => {
    const tamanhoEstimadoDoBloco = TAMANHO_BLOCO_PADRAO * BYTES_POR_ELEMENTO_PIOR_CASO;
    expect(tamanhoEstimadoDoBloco).toBeLessThan(LIMITE_KAFKA_BYTES * MARGEM_ENVELOPE_JSON);
  });
});
