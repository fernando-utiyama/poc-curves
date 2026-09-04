export function formatarDataDDMMAAAA(referenceDate: string): string {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(referenceDate);
  if (!match) {
    throw new Error(`referenceDate deve estar no formato YYYY-MM-DD: recebido "${referenceDate}"`);
  }
  const [, ano, mes, dia] = match;
  return `${dia}/${mes}/${ano}`;
}
