import Decimal from "decimal.js";

Decimal.set({ precision: 40, rounding: Decimal.ROUND_HALF_UP });

type DecimalInput = Decimal.Value;
type Double = number;

export interface VerticeInput {
  data: string;
  dc: number;
  du: number;
  txCdi: DecimalInput;
}

export interface VerticeCalculado extends VerticeInput {
  fatorDiario: Double;
  fatorAcumulado: Double;
}

function toDecimal(value: DecimalInput): Decimal {
  return new Decimal(value);
}

function calcFatorDiarioDecimal(txCdi: DecimalInput): Decimal {
  return toDecimal(1)
    .plus(toDecimal(txCdi).div(100))
    .pow(toDecimal(1).div(252));
}

export function calcFatorDiario(txCdi: DecimalInput): Double {
  return calcFatorDiarioDecimal(txCdi).toNumber();
}

export const calcularFatorDiario = calcFatorDiario;

function calcFatorAcumuladoDecimal(
  fatorAcumuladoAnterior: DecimalInput,
  fatorDiarioAnterior: DecimalInput,
  duAtual: number,
  duAnterior: number,
): Decimal {
  return toDecimal(fatorAcumuladoAnterior).times(
    calcFatorAcumuladoPeriodoDecimal(fatorDiarioAnterior, duAtual, duAnterior),
  );
}

export function calcFatorAcumulado(
  fatorAcumuladoAnterior: DecimalInput,
  fatorDiarioAnterior: DecimalInput,
  duAtual: number,
  duAnterior: number,
): Double {
  return calcFatorAcumuladoDecimal(
    fatorAcumuladoAnterior,
    fatorDiarioAnterior,
    duAtual,
    duAnterior,
  ).toNumber();
}

function calcFatorAcumuladoPeriodoDecimal(
  fatorDiario: DecimalInput,
  duAtual: number,
  duReferencia: number,
): Decimal {
  return toDecimal(fatorDiario).pow(duAtual - duReferencia);
}

export function calcFatorAcumuladoPeriodo(
  fatorDiario: DecimalInput,
  duAtual: number,
  duReferencia: number,
): Double {
  return calcFatorAcumuladoPeriodoDecimal(fatorDiario, duAtual, duReferencia).toNumber();
}

export const calcularFatorAcumulado = calcFatorAcumulado;

export function calcDiff(
  fatorAcumuladoCalculado: DecimalInput,
  fatorAcumuladoOriginal: DecimalInput,
): Double {
  return toDecimal(fatorAcumuladoCalculado).minus(toDecimal(fatorAcumuladoOriginal)).toNumber();
}

export const calcularDiferencaFatorAcumulado = calcDiff;

export function calcularVertices(vertices: VerticeInput[]): VerticeCalculado[] {
  if (!vertices.length) return [];

  const resultado: VerticeCalculado[] = [];
  let fatorDiarioAnterior = new Decimal(1);
  let fatorAcumuladoAnterior = new Decimal(1);
  let duAnterior = vertices[0].du;

  for (let i = 0; i < vertices.length; i += 1) {
    const vertice = vertices[i];
    const fatorDiarioDecimal = calcFatorDiarioDecimal(vertice.txCdi);

    let fatorAcumuladoDecimal: Decimal;

    if (i === 0) {
      fatorAcumuladoDecimal = new Decimal(1);
    } else {
      fatorAcumuladoDecimal = calcFatorAcumuladoDecimal(
        fatorAcumuladoAnterior,
        fatorDiarioAnterior,
        vertice.du,
        duAnterior,
      );
    }

    const fatorDiario = fatorDiarioDecimal.toNumber();
    const fatorAcumulado = fatorAcumuladoDecimal.toNumber();

    resultado.push({
      ...vertice,
      fatorDiario,
      fatorAcumulado,
    });

    fatorDiarioAnterior = fatorDiarioDecimal;
    fatorAcumuladoAnterior = fatorAcumuladoDecimal;
    duAnterior = vertice.du;
  }

  return resultado;
}

export function fmt(value: DecimalInput, casas = 10): string {
  return toDecimal(value).toFixed(casas);
}
