import { describe, expect, it, vi, beforeAll } from 'vitest';
import { calcularEventId, calcularLoteId } from './lote-id.js';
import { dividirEmBlocos } from './blocos.js';
import { montarRecords } from './registros-payload.js';
import { publicarBloco } from './kafka-publisher.js';
import type { EventEnvelope } from './envelope.js';

const registrosSinteticos = [1, 2, 3, 4, 5].map((n) => Buffer.from(`registro-${n}`, 'utf-8'));
const conteudoSintetico = Buffer.concat(registrosSinteticos);

describe('publicação sequencial de lote em múltiplos blocos', () => {
  const correlationId = 'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a33';
  const source = 'B3';
  const dataset = 'B3_TAXA_SWAP_DCL';
  const referenceDate = '2026-08-21';
  const producedAt = '2026-08-21T21:00:00Z';
  const schemaVersion = '1.0';
  const payloadKind = 'INDIVIDUAL_QUOTES';

  const loteId = calcularLoteId(source, dataset, referenceDate, conteudoSintetico);
  const blocos = dividirEmBlocos(registrosSinteticos, 2);
  const enviarFake = vi.fn().mockResolvedValue(undefined);
  const envelopes: EventEnvelope[] = [];

  beforeAll(async () => {
    for (const bloco of blocos) {
      const eventId = calcularEventId(loteId, bloco.sequencia);
      const resultado = await publicarBloco(
        {
          envelope: {
            eventId,
            correlationId,
            source,
            dataset,
            referenceDate,
            producedAt,
            schemaVersion,
            payloadKind,
            loteId,
            sequencia: bloco.sequencia,
            totalBlocos: bloco.totalBlocos,
            payload: {
              sourceUrl: 'file://fixture-teste',
              encoding: 'utf-8',
              contentHash: `sha256:${'a'.repeat(64)}`,
              sizeBytes: conteudoSintetico.length,
              records: montarRecords(bloco, 'utf-8'),
            },
          },
          faixa: 'ROTINA',
        },
        enviarFake,
      );
      envelopes.push(resultado.envelope);
    }
  });

  it('divide os registros sintéticos em exatamente 3 blocos', () => {
    expect(blocos).toHaveLength(3);
  });

  it('garante que os 3 envelopes têm exatamente o mesmo loteId calculado', () => {
    expect(envelopes).toHaveLength(3);
    for (const envelope of envelopes) {
      expect(envelope.loteId).toBe(loteId);
    }
  });

  it('garante que as sequencias dos 3 envelopes são [1, 2, 3], nessa ordem', () => {
    const sequencias = envelopes.map((envelope) => envelope.sequencia);
    expect(sequencias).toEqual([1, 2, 3]);
  });

  it('garante que o totalBlocos dos 3 envelopes é 3 em todos', () => {
    for (const envelope of envelopes) {
      expect(envelope.totalBlocos).toBe(3);
    }
  });

  it('garante que os 3 eventId são todos diferentes entre si', () => {
    const eventIds = envelopes.map((envelope) => envelope.eventId);
    const eventIdsUnicos = new Set(eventIds);
    expect(eventIdsUnicos.size).toBe(3);
  });

  it('garante que enviarFake foi chamado exatamente 3 vezes', () => {
    expect(enviarFake).toHaveBeenCalledTimes(3);
  });
});
