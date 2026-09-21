export type CurveCatalogEntry = {
  code: string;
  aliases?: string[];
  ticker?: string;
  curveType: string;
  assetClass?: string;
  descriptionMatchers?: RegExp[];
};

type CurveCatalogDefinition = readonly [
  code: string,
  ticker: string,
  curveType: string,
  assetClass: string,
  descriptionMatchers?: RegExp[],
];

const FX_CODES = ["ARB", "ARS", "AUD", "CAD", "CHF", "CLP", "CNY"] as const;

const CATALOG_DEFINITIONS = [
  ["ACC", "DOL", "CUPOM_CAMBIAL", "CAMBIO", [/DOL/i, /Cupo/i]],
  ["APR", "PRE", "PRE", "JUROS", [/PRE/i, /Aj\. PRE/i]],
  // TODO: confirmar com o usuario - linha cortada na foto por um popup do
  // editor (tooltip de tipo inferido sobre o parametro `code`). Esta e a
  // melhor reconstrucao possivel a partir do que ficou visivel
  // (`[code, code, "FX", "` antes do corte) - falta confirmar o valor de
  // assetClass (aqui assumido "CAMBIO", por analogia com a linha ACC/DOL).
  ...FX_CODES.map((code): CurveCatalogDefinition => [code, code, "FX", "CAMBIO"]),
  ["BIT", "BIT", "CRYPTO", "CRYPTO"],
  ["BRP", "BRP", "INDICE_PRE", "INDICE"],
  ["CNL", "CNL", "FUTURO", "JUROS"],
  ["CYI", "CYI", "YIELD", "JUROS"],
  ["CYM", "CYM", "YIELD", "JUROS"],
  ["DCL", "DCL", "CUPOM_LIMPO", "JUROS"],
  ["TR", "TR", "TR", "JUROS", [/TR/i]],
  ["TPR", "LTN", "TESOURO_PRE", "JUROS", [/LTN/i]],
  // Titulos publicos: mesmo instrumento aparece com codigo numerico e de 3
  // letras; os descriptionMatchers resolvem ambos para o mesmo ticker.
  ["021", "LFT", "TESOURO_SELIC", "JUROS", [/^LFT$/i]],
  ["076", "NTN-B", "TESOURO_IPCA", "JUROS", [/^NTN-B$/i]],
  ["077", "NTN-C", "TESOURO_IGPM", "JUROS", [/^NTN-C$/i]],
  ["095", "NTN-F", "TESOURO_PRE", "JUROS", [/^NTN-F$/i]],
] satisfies CurveCatalogDefinition[];

function toCurveCatalogEntry([
  code,
  ticker,
  curveType,
  assetClass,
  descriptionMatchers,
]: CurveCatalogDefinition): CurveCatalogEntry {
  return {
    code,
    ticker,
    curveType,
    assetClass,
    ...(descriptionMatchers ? { descriptionMatchers } : {}),
  };
}

export const CURVE_TYPE_CATALOG: CurveCatalogEntry[] = CATALOG_DEFINITIONS.map(
  toCurveCatalogEntry,
);
