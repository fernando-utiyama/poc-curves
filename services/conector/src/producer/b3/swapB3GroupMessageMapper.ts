import {
  KafkaGroupedSwapMessage,
  KafkaGroupedSwapValues,
  KafkaSwapVertex,
} from "../../models/b3/kafkaB3GroupedSwapMessage";
import { calcularVertices } from "./curveB3Factors";

type GroupableRecord = {
  descricao?: string | null;
  ticker?: string | null;
  dataBase?: string | null;
  diasCorridos?: number | null;
  diasUteis?: number | null;
  valor?: number | null;
};

type LoggerLike = {
  warn?: (...args: any[]) => void;
  info?: (...args: any[]) => void;
  error?: (...args: any[]) => void;
};

function formatDateToIso(value?: string | null): string {
  const raw = String(value ?? "");

  if (!/^\d{8}$/.test(raw)) {
    return raw;
  }

  return `${raw.slice(0, 4)}-${raw.slice(4, 6)}-${raw.slice(6, 8)}`;
}

/**
 * Chave de agrupamento pelo ticker do registro. Cada ticker distinto vira um
 * agrupamento próprio. Sem ticker, usa a `descricao` como fallback.
 */
function groupingKey(record: GroupableRecord): string {
  const ticker = String(record.ticker ?? "").trim().toUpperCase();

  if (ticker) {
    return ticker;
  }

  return String(record.descricao ?? "").trim().toUpperCase() || "DESCONHECIDO";
}

function isCalculableRecord(record: GroupableRecord): boolean {
  return (
    Number.isFinite(record.diasCorridos) &&
    Number.isFinite(record.diasUteis) &&
    Number.isFinite(record.valor) &&
    Number(record.valor) > -100
  );
}

function isFiniteVertex(vertice: { fatorDiario: number; fatorAcumulado: number }): boolean {
  return Number.isFinite(vertice.fatorDiario) && Number.isFinite(vertice.fatorAcumulado);
}

/**
 * Agrupa todos os registros de swap por ticker. Quando um fator não puder ser
 * calculado no domínio real, preserva os dados originais e publica fator nulo.
 */
export function toKafkaGroupedSwapMessages(
  records: GroupableRecord[],
  logger: LoggerLike = console,
): KafkaGroupedSwapMessage[] {
  const groups = new Map<string, GroupableRecord[]>();

  for (const record of records) {
    const key = groupingKey(record);
    const bucket = groups.get(key);

    if (bucket) {
      bucket.push(record);
    } else {
      groups.set(key, [record]);
    }
  }

  const messages: KafkaGroupedSwapMessage[] = [];

  for (const [key, groupRecords] of groups) {
    const calculableRecords = groupRecords
      .map((record, index) => ({ record, index }))
      .filter(({ record }) => isCalculableRecord(record));
    const calculatedByIndex = new Map<number, ReturnType<typeof calcularVertices>[number]>();

    try {
      const calculated = calcularVertices(
        calculableRecords.map(({ record }) => ({
          data: String(record.dataBase ?? ""),
          dc: Number(record.diasCorridos),
          du: Number(record.diasUteis),
          txCdi: Number(record.valor),
        })),
      );

      calculated.forEach((vertex, index) => {
        calculatedByIndex.set(calculableRecords[index].index, vertex);
      });
    } catch (error) {
      const message = error instanceof Error ? error.message : String(error);
      logger.error?.(
        `[swapB3GroupMessageMapper] Falha ao calcular fatores do grupo ${key}: ${message}`,
      );
    }

    const vertices: KafkaSwapVertex[] = groupRecords.map((record, index) => {
      const calculated = calculatedByIndex.get(index);
      const hasFiniteFactors: boolean = calculated ? isFiniteVertex(calculated) : false;

      return {
        diasCorridos: Number.isFinite(record.diasCorridos)
          ? Number(record.diasCorridos)
          : null,
        diasUteis: Number.isFinite(record.diasUteis) ? Number(record.diasUteis) : null,
        valor: Number.isFinite(record.valor) ? Number(record.valor) : null,
        fatorDiario: hasFiniteFactors ? calculated!.fatorDiario : null,
        fatorAcumulado: hasFiniteFactors ? calculated!.fatorAcumulado : null,
      };
    });

    const retainedWithoutFactors = vertices.filter(
      (vertex) => vertex.fatorDiario === null || vertex.fatorAcumulado === null,
    ).length;

    if (retainedWithoutFactors > 0) {
      logger.warn?.(
        `[swapB3GroupMessageMapper] Grupo ${key}: ${retainedWithoutFactors} registro(s) preservado(s) com fator nulo.`,
      );
    }

    const values: KafkaGroupedSwapValues = {
      ticker: key,
      refDate: formatDateToIso(groupRecords[0].dataBase),
      vertices,
    };

    messages.push({ values });
  }

  return messages;
}
