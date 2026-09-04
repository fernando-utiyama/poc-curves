import type { Bloco } from './blocos.js';

/**
 * Constrói o array `payload.records` (contracts/events/marketdata-raw.schema.json)
 * a partir de um bloco de registros brutos (`Buffer[]`, corte estrutural de
 * xml-estrutural.ts), decodificando cada registro no encoding declarado pela
 * fonte. Cada item vira `{ raw: <texto decodificado> }` — o formato que
 * `IngestaoListener.reconstruirConteudo` (curve-processor) espera como
 * caminho primário.
 * <p>
 * Usa `TextDecoder` em vez de `Buffer.toString`, porque o encoding declarado
 * pela fonte (ex.: "ISO-8859-1", "WINDOWS-1252") nem sempre é um dos poucos
 * nomes que `Buffer.toString` aceita — `TextDecoder` reconhece os rótulos
 * IANA reais via ICU.
 *
 * @throws Error (via TextDecoder, `fatal: true`) se o conteúdo não for válido no encoding declarado
 */
export function montarRecords(
  bloco: Bloco<Buffer>,
  encoding: string,
): ReadonlyArray<{ raw: string }> {
  const decoder = new TextDecoder(encoding, { fatal: true });
  return bloco.registros.map((registro) => ({ raw: decoder.decode(registro) }));
}
