/**
 * Formata uma `referenceDate` (YYYY-MM-DD) no padrão `AAMMDD` (ano com 2
 * dígitos, mês, dia) usado por nome de arquivo de mais de uma fonte —
 * confirmado real tanto para a B3 (`PR260821.zip`, `IN260821.zip`,
 * `TS260821.ex_`) quanto para a ANBIMA (`ms260821.txt`), ambas para
 * referenceDate `2026-08-21`. Coincidência de convenção entre as duas
 * fontes, não uma regra do contrato — cada fonte decide seu próprio nome de
 * arquivo; isto só evita duplicar a mesma lógica de formatação de data.
 *
 * @throws Error se referenceDate não estiver no formato YYYY-MM-DD
 */
export function formatarDataAAMMDD(referenceDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(referenceDate);
  if (!match) {
    throw new Error(`referenceDate deve estar no formato YYYY-MM-DD: recebido "${referenceDate}"`);
  }
  const [, ano, mes, dia] = match;
  return `${(ano as string).slice(2)}${mes}${dia}`;
}
