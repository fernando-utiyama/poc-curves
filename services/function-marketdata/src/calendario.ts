/**
 * Calendário de pregão B3/ANBIMA: feriados nacionais fixos e móveis, e a
 * decisão de dia de pregão. Feriados móveis calculados pelo algoritmo de
 * Páscoa gregoriano (Meeus/Jones/Butcher) — verificados contra o calendário
 * oficial ANBIMA de feriados nacionais de 2026 (anbima.com.br/feriados).
 * Não inclui feriados municipais/estaduais — a B3 segue o calendário nacional.
 */

/** Domingo de Páscoa de um ano (mês 1-12, dia 1-31), algoritmo de Meeus/Jones/Butcher. */
function calcularDomingoPascoa(ano: number): { mes: number; dia: number } {
  const a = ano % 19;
  const b = Math.floor(ano / 100);
  const c = ano % 100;
  const d = Math.floor(b / 4);
  const e = b % 4;
  const f = Math.floor((b + 8) / 25);
  const g = Math.floor((b - f + 1) / 3);
  const h = (19 * a + b - d - g + 15) % 30;
  const i = Math.floor(c / 4);
  const k = c % 4;
  const l = (32 + 2 * e + 2 * i - h - k) % 7;
  const m = Math.floor((a + 11 * h + 22 * l) / 451);
  const mes = Math.floor((h + l - 7 * m + 114) / 31);
  const dia = ((h + l - 7 * m + 114) % 31) + 1;
  return { mes, dia };
}

function adicionarDias(data: Date, dias: number): Date {
  const resultado = new Date(data);
  resultado.setUTCDate(resultado.getUTCDate() + dias);
  return resultado;
}

function formatarISO(data: Date): string {
  return data.toISOString().slice(0, 10);
}

/** Feriados nacionais fixos, espelhando o calendário ANBIMA. */
const FERIADOS_FIXOS: ReadonlyArray<{ mes: number; dia: number }> = [
  { mes: 1, dia: 1 }, // Confraternização Universal
  { mes: 4, dia: 21 }, // Tiradentes
  { mes: 5, dia: 1 }, // Dia do Trabalho
  { mes: 9, dia: 7 }, // Independência
  { mes: 10, dia: 12 }, // Nossa Senhora Aparecida
  { mes: 11, dia: 2 }, // Finados
  { mes: 11, dia: 15 }, // Proclamação da República
  { mes: 11, dia: 20 }, // Consciência Negra — feriado nacional desde 2024 (Lei 14.759/2023)
  { mes: 12, dia: 25 }, // Natal
];

/**
 * Feriados nacionais (fixos + móveis) de um ano, no formato YYYY-MM-DD.
 * Feriados móveis: Carnaval (segunda e terça, Páscoa -48 e -47 dias),
 * Sexta-feira Santa/Paixão de Cristo (Páscoa -2 dias), Corpus Christi
 * (Páscoa +60 dias). Quinta-feira Santa NÃO é feriado — Resolução ANBIMA
 * nº 2.516 (dia útil desde 2000).
 */
export function feriadosDoAno(ano: number): ReadonlySet<string> {
  const feriados = new Set<string>();

  for (const { mes, dia } of FERIADOS_FIXOS) {
    feriados.add(formatarISO(new Date(Date.UTC(ano, mes - 1, dia))));
  }

  const pascoa = calcularDomingoPascoa(ano);
  const domingoPascoa = new Date(Date.UTC(ano, pascoa.mes - 1, pascoa.dia));

  feriados.add(formatarISO(adicionarDias(domingoPascoa, -48))); // Carnaval — segunda
  feriados.add(formatarISO(adicionarDias(domingoPascoa, -47))); // Carnaval — terça
  feriados.add(formatarISO(adicionarDias(domingoPascoa, -2))); // Sexta-feira Santa
  feriados.add(formatarISO(adicionarDias(domingoPascoa, 60))); // Corpus Christi

  return feriados;
}

const ANO_MINIMO_SUPORTADO = 2020;
const ANO_MAXIMO_SUPORTADO = 2035;

/**
 * Decide se uma data (YYYY-MM-DD) é dia de pregão B3: não é sábado, não é
 * domingo, e não é feriado nacional. Datas fora do intervalo suportado
 * falham explicitamente — nunca assume que uma data fora do intervalo é
 * dia útil.
 *
 * @throws Error se dataISO não for uma data válida, ou se o ano estiver fora
 *               do intervalo [2020, 2035]
 */
export function ehDiaDePregao(dataISO: string): boolean {
  const data = new Date(`${dataISO}T00:00:00Z`);
  if (Number.isNaN(data.getTime())) {
    throw new Error(`data inválida: "${dataISO}"`);
  }

  const ano = data.getUTCFullYear();
  if (ano < ANO_MINIMO_SUPORTADO || ano > ANO_MAXIMO_SUPORTADO) {
    throw new Error(
      `ano ${ano} fora do intervalo suportado pelo calendário [${ANO_MINIMO_SUPORTADO}, ${ANO_MAXIMO_SUPORTADO}]`,
    );
  }

  const diaDaSemana = data.getUTCDay(); // 0 = domingo, 6 = sábado
  if (diaDaSemana === 0 || diaDaSemana === 6) {
    return false;
  }

  return !feriadosDoAno(ano).has(dataISO);
}
