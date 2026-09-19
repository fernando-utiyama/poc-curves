import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { describe, expect, it } from 'vitest';
import { validarEnvelope } from './validador-envelope.js';
import type { EventEnvelope } from './envelope.js';

const __dirname = dirname(fileURLToPath(import.meta.url));
const dirFixtures = join(__dirname, '..', 'contracts', 'events', 'fixtures');

/**
 * Teste de contrato cruzado (tarefa 2.6 da mudança curves-solution-architecture):
 * carrega os MESMOS exemplos reais de envelope
 * (contracts/events/fixtures/*.json — fonte única, sincronizada aqui por
 * `npm run sync-contracts`, e copiada para o classpath de services/common
 * via pom.xml) e prova que o lado produtor (este teste — validador
 * TypeScript, ajv, o mesmo que `publicarBloco`/`kafka-publisher.ts` chama
 * antes de todo envio real) e o lado consumidor
 * (EnvelopeContratoCruzadoTest.java, validador Java networknt E
 * deserialização real para `EventEnvelope`) concordam sobre o que é um
 * envelope válido.
 */
describe('contrato cruzado: fixture real de envelope validada pelo lado produtor (ajv)', () => {
  it.each(['envelope-valido-exemplo.json', 'envelope-manual-valido-exemplo.json'])(
    '%s é válida contra o schema, sem lançar EnvelopeInvalidoError',
    (nomeArquivo) => {
      const conteudo = readFileSync(join(dirFixtures, nomeArquivo), 'utf-8');
      const envelope = JSON.parse(conteudo) as EventEnvelope;

      expect(() => validarEnvelope(envelope)).not.toThrow();
    },
  );
});
