import type { ParsedB3Line } from "../../domain/b3/typesB3";

function safeSlice(line: string, start: number, end: number): string {
  return String(line ?? "").slice(start, end);
}

function toInt(rawValue: string | null | undefined): number | null {
  const digits = String(rawValue ?? "").replace(/[^\d]/g, "");
  return digits ? Number(digits) : null;
}

function parseSignedDecimal(rawValue: string | null | undefined, scale = 7): number | null {
  const value = String(rawValue ?? "").trim();

  if (!value) {
    return null;
  }

  const sign = value.startsWith("-") ? -1 : 1;
  const digits = value.replace(/[^\d]/g, "");

  if (!digits) {
    return null;
  }

  return sign * (Number(digits) / Math.pow(10, scale));
}

export function parseB3Line(line: string): ParsedB3Line | null {
  const raw = String(line ?? "");

  if (!raw.trim()) {
    return null;
  }

  if (raw.length < 72) {
    return null;
  }

  const sequencial = safeSlice(raw, 0, 6).trim();
  const complementoTransacao = safeSlice(raw, 6, 9).trim();
  const tipoRegistro = safeSlice(raw, 9, 11).trim();
  const dataBase = safeSlice(raw, 11, 19).trim();
  const codigoCurva = safeSlice(raw, 19, 21).trim();
  const codigoTaxa = safeSlice(raw, 21, 26).trim();
  const rawCurveCode = codigoTaxa;
  const curveCode = codigoTaxa;
  const descricao = safeSlice(raw, 26, 41).trim();
  const diasCorridos = toInt(safeSlice(raw, 41, 46));
  const diasUteis = toInt(safeSlice(raw, 46, 51));
  const sinalTaxa = safeSlice(raw, 51, 52).trim() || null;
  const valor = parseSignedDecimal(`${sinalTaxa ?? ""}${safeSlice(raw, 52, 66)}`, 7);
  const flagMercado = safeSlice(raw, 66, 67).trim() || null;
  const codigoVertice = toInt(safeSlice(raw, 67, 72));
  const headerChunk = safeSlice(raw, 19, 41);

  return {
    sequencial,
    complementoTransacao,
    tipoRegistro,
    dataBase,
    codigoCurva,
    codigoTaxa,
    rawCurveCode,
    curveCode,
    descricao,
    diasCorridos,
    diasUteis,
    valor,
    sinalTaxa,
    flagMercado,
    codigoVertice,
    linhaOriginal: raw,
    headerChunk,
  };
}
