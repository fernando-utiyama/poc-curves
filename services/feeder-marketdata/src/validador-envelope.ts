import { Ajv2020 } from 'ajv/dist/2020.js';
// ajv-formats é CommonJS mas seu .d.ts é escrito com `export default` — sob
// moduleResolution NodeNext, o TypeScript resolve o import como o namespace
// do módulo inteiro em vez do valor default (mesmo comportamento com
// `import addFormats from` e com `import addFormats = require`, verificado
// nesta versão do compilador). O require + cast é o contorno: em runtime
// `require('ajv-formats')` é a própria função plugin (o módulo faz
// `module.exports = exports = formatsPlugin`), então o cast só corrige o
// tipo estático para bater com o que já é verdade em runtime.
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const addFormats = require('ajv-formats') as (ajv: Ajv2020) => void;
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import type { EventEnvelope } from './envelope.js';
import type { AnySchemaObject } from 'ajv';

const __dirname = dirname(fileURLToPath(import.meta.url));
// contracts/events é sibling de src (dev, sincronizado por `npm run sync-contracts`)
// e de dist (container, copiado no Containerfile) — mesma profundidade nos dois casos.
const caminhoSchema = join(__dirname, '..', 'contracts', 'events', 'envelope.schema.json');
const schema = JSON.parse(readFileSync(caminhoSchema, 'utf-8')) as AnySchemaObject;

/**
 * `strict: false` porque o schema usa `if`/`then` referenciando `submittedBy`
 * e `justification` (declarados em `properties` no nível raiz, não repetidos
 * dentro de `then`) — um padrão válido em JSON Schema que o modo estrito do
 * Ajv rejeita por engano (verificado: `strict: true` lança
 * "required property 'submittedBy' is not defined" na compilação, mesmo o
 * campo existindo em `properties`).
 */
const ajv = new Ajv2020({ allErrors: true, strict: false });
addFormats(ajv);
const validar = ajv.compile(schema);

/** Lançado quando um envelope não é conforme ao contrato de contracts/events/envelope.schema.json. */
export class EnvelopeInvalidoError extends Error {
  constructor(erros: string) {
    super(`envelope não conforme ao schema: ${erros}`);
    this.name = 'EnvelopeInvalidoError';
  }
}

/**
 * Valida um envelope já montado contra contracts/events/envelope.schema.json,
 * antes do envio ao Kafka — o produtor nunca publica um evento fora do
 * contrato.
 *
 * @throws EnvelopeInvalidoError se o envelope não for conforme ao schema
 */
export function validarEnvelope(envelope: EventEnvelope): void {
  const valido = validar(envelope);
  if (!valido) {
    const erros = (validar.errors ?? [])
      .map((erro) => `${erro.instancePath || '/'} ${erro.message ?? ''}`.trim())
      .join('; ');
    throw new EnvelopeInvalidoError(erros);
  }
}
