import { KafkaSwapMessage, KafkaSwapValues } from "../../models/b3/kafkaB3SwapMessage";
import { SwapRecord } from "../../models/b3/swapB3Record";
import { calcularVertices } from "./curveB3Factors";

type FactorFields = Pick<KafkaSwapValues, "fatorDiario" | "fatorAcumulado">;

function formatDateToIso(value: string): string {
  if (!/^\d{8}$/.test(value)) {
    return value;
  }

  const year = value.slice(0, 4);
  const month = value.slice(4, 6);
  const day = value.slice(6, 8);

  return `${year}-${month}-${day}`;
}

function toDouble(value: unknown, fieldName: string): number {
  if (value === null || value === undefined || value === "") {
    throw new Error(`${fieldName} inválido para payload Kafka.`);
  }

  const parsed = Number(value);

  if (!Number.isFinite(parsed)) {
    throw new Error(`${fieldName} inválido para payload Kafka.`);
  }

  return parsed;
}

export function toKafkaSwapMessage(
  record: SwapRecord,
  factors?: FactorFields,
): KafkaSwapMessage {
  const valor = toDouble(record.valor, "valor");
  const resolvedFactors: FactorFields = factors || calcularVertices([{
    data: record.dataBase,
    dc: toDouble(record.diasCorridos, "diasCorridos"),
    du: toDouble(record.diasUteis, "diasUteis"),
    txCdi: valor,
  }])[0];

  return {
    values: {
      ticker: record.ticker,
      refDate: formatDateToIso(record.dataBase),
      diasCorridos: toDouble(record.diasCorridos, "diasCorridos"),
      diasUteis: toDouble(record.diasUteis, "diasUteis"),
      valor,
      fatorDiario: toDouble(resolvedFactors.fatorDiario, "fatorDiario"),
      fatorAcumulado: toDouble(resolvedFactors.fatorAcumulado, "fatorAcumulado"),
    },
  };
}

export function toKafkaSwapMessages(
  records: SwapRecord[],
): KafkaSwapMessage[] {
  const vertices = calcularVertices(
    records.map((record) => ({
      data: record.dataBase,
      dc: toDouble(record.diasCorridos, "diasCorridos"),
      du: toDouble(record.diasUteis, "diasUteis"),
      txCdi: toDouble(record.valor, "valor"),
    })),
  );

  return records.map((record, index) => toKafkaSwapMessage(record, {
    fatorDiario: vertices[index].fatorDiario,
    fatorAcumulado: vertices[index].fatorAcumulado,
  }));
}
