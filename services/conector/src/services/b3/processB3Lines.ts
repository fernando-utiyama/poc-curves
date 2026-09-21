type LoggerLike = {
  warn?: (...args: any[]) => void;
  info?: (...args: any[]) => void;
  error?: (...args: any[]) => void;
  log?: (...args: any[]) => void;
};

type ProcessedB3Record = Record<string, any>;

const { normalizeCurveType } = require("../../domain/b3/normalizeB3CurveType");
const { parseB3Line } = require("./b3SwapParser");

function processLines(lines: string[], logger: LoggerLike = console): ProcessedB3Record[] {
  const parsed: ProcessedB3Record[] = [];
  const unknownTypes = new Set<string>();
  let discarded = 0;

  logger.info?.(`[B3] processLines iniciado. totalLines=${lines.length}`);

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];

    if (index < 5) {
      logger.info?.(
        `[B3] linha[${index}] len=${String(line ?? "").length} content="${String(line ?? "")}"`,
      );
    }

    const item = parseB3Line(line);

    if (!item) {
      discarded += 1;

      if (index < 5) {
        logger.warn?.(`[B3] linha[${index}] descartada no parser`);
      }

      continue;
    }

    if (index < 5) {
      logger.info?.(`[B3] linha[${index}] parseada=${JSON.stringify(item)}`);
    }

    const normalized = normalizeCurveType(
      item.curveCode || item.rawCurveCode,
      item.descricao,
    );

    if (index < 5) {
      logger.info?.(`[B3] linha[${index}] normalizada=${JSON.stringify(normalized)}`);
    }

    const result: ProcessedB3Record = {
      ...item,
      ...normalized,
    };

    if (!normalized.knownType) {
      unknownTypes.add(
        `${item.curveCode || item.rawCurveCode || "SEM_CODIGO"}|${item.descricao || "SEM_DESCRICAO"}`,
      );
    }

    parsed.push(result);
  }

  if (unknownTypes.size > 0 && logger.warn) {
    logger.warn("[B3] Tipos de curva não mapeados encontrados:");

    for (const entry of unknownTypes) {
      logger.warn(`[B3] ${entry}`);
    }
  }

  logger.info?.(
    `[B3] processLines finalizado. totalParsed=${parsed.length} totalDiscarded=${discarded} totalUnknown=${unknownTypes.size}`,
  );

  return parsed;
}

module.exports = {
  processLines,
};

export {};
