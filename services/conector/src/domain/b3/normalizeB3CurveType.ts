import { CURVE_TYPE_CATALOG } from "./curveB3TypeCatalog";
import type { NormalizedCurveType } from "./typesB3";

export function normalizeCurveType(curveCode?: string, descricao?: string): NormalizedCurveType {
  const rawCode = String(curveCode ?? "").trim().toUpperCase();
  const code = rawCode.replace(/^T1/, "").trim();
  const desc = String(descricao ?? "").trim();

  const match = CURVE_TYPE_CATALOG.find((entry) => {
    if (entry.code === code) {
      return true;
    }

    if (entry.aliases?.some((alias) => alias.toUpperCase() === code)) {
      return true;
    }

    if (entry.descriptionMatchers?.some((regex) => regex.test(desc))) {
      return true;
    }

    return false;
  });

  if (!match) {
    return {
      knownType: false,
      ticker: code || null,
      curveType: null,
      assetClass: null,
      sourceCode: code || null,
    };
  }

  return {
    knownType: true,
    ticker: match.ticker ?? code,
    curveType: match.curveType,
    assetClass: match.assetClass ?? null,
    sourceCode: match.code,
  };
}
