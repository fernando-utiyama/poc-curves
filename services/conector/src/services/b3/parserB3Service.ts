const { processLines } = require("./processB3Lines");
const layout = require("../../layouts/b3/swapB3Layout.json");

function safeSlice(line: string, start: number, end: number): string {
  return String(line ?? "").slice(start, end);
}

function toInt(raw: string | null | undefined): number | null {
  const digits = String(raw ?? "").replace(/[^\d]/g, "");
  return digits ? Number(digits) : null;
}

function parseSignedFromRaw(raw: string | null | undefined, divisorOverride?: number): number {
  const value = String(raw ?? "").trim();

  if (!value) return 0;

  const sign = value.startsWith("-") ? -1 : 1;
  const digits = value.replace(/[^\d]/g, "");
  if (!digits) return 0;

  const div = typeof divisorOverride === "number" ? divisorOverride : (layout.divisor || 10000000);
  return sign * (Number(digits) / Number(div));
}

type LoggerLike = {
  log?: (...args: any[]) => void;
  info?: (...args: any[]) => void;
  warn?: (...args: any[]) => void;
  error?: (...args: any[]) => void;
};

export function parseFile(rawContent: string, logger: LoggerLike = console) {
  const safeContent = String(rawContent ?? "");

  logger.log?.(`[parserService] Iniciando parseFile. rawLength=${safeContent.length}`);

  const normalizedContent = safeContent
    .replace(/\r\n/g, "\n")
    .replace(/\r/g, "\n");

  logger.log?.(
    `[parserService] Conteúdo normalizado. normalizedLength=${normalizedContent.length}`,
  );

  const lines = normalizedContent
    .split("\n")
    .map((line) => line.trimEnd())
    .filter((line) => line.trim().length > 0);

  logger.log?.(`[parserService] Split concluído. totalLines=${lines.length}`);

  for (let i = 0; i < Math.min(lines.length, 5); i += 1) {
    logger.log?.(
      `[parserService] preview linha[${i}] len=${lines[i].length} content="${lines[i]}"`,
    );
  }

  const records = processLines(lines, logger);

  logger.log?.(`[parserService] processLines retornou totalRecords=${records.length}`);

  return records;
}

export function parseLine(rawLine: string) {
  const raw = String(rawLine ?? "");

  if (!raw.trim()) return null;
  if (raw.length < (layout.lineMinLength || 0)) return null;

  const result: Record<string, any> = {};

  for (const field of layout.fields) {
    const piece = safeSlice(raw, field.start, field.end);

    if (field.type === "number") {
      result[field.name] = toInt(piece) ?? null;
    } else {
      result[field.name] = field.trim ? String(piece).trim() : piece;
    }
  }

  const ticker = String(result.ticker ?? "");

  if (!ticker) {
    return null;
  }

  const taxaField = layout.fields.find((field) => field.name === "taxaRaw");
  const taxaRaw = safeSlice(raw, taxaField.start, taxaField.end).trim();
  result.taxaRaw = taxaRaw;
  result.valor = parseSignedFromRaw(`${result.sinalTaxa ?? ""}${taxaRaw}`, layout.divisor);

  result.diasUteis = result.diasUteis == null ? null : Number(result.diasUteis);
  result.diasCorridos = result.diasCorridos == null ? null : Number(result.diasCorridos);
  result.codigoVertice = result.codigoVertice == null ? null : Number(result.codigoVertice);

  result.fonteLayout = layout.source;

  return result;
}
