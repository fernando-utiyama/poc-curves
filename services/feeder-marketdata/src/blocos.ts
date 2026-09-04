/** Um bloco de registros dentro de um lote, com sua posição e o total de blocos do lote. */
export interface Bloco<T> {
  readonly sequencia: number;
  readonly totalBlocos: number;
  readonly registros: readonly T[];
}

/**
 * Divide uma lista de registros já parseados em blocos de até `tamanhoBloco`
 * itens cada. O corte é estrutural ("esta lista tem N elementos"), nunca
 * semântico — quem decide o que é um "registro" é o parser do dataset, não
 * esta função.
 *
 * @throws Error se tamanhoBloco for menor que 1, ou se registros for vazio
 */
export function dividirEmBlocos<T>(registros: readonly T[], tamanhoBloco: number): Bloco<T>[] {
  if (tamanhoBloco < 1) {
    throw new Error(`tamanhoBloco deve ser >= 1: recebido ${tamanhoBloco}`);
  }
  if (registros.length === 0) {
    throw new Error('registros não pode ser vazio');
  }

  const totalBlocos = Math.ceil(registros.length / tamanhoBloco);
  const blocos: Bloco<T>[] = [];
  for (let i = 0; i < totalBlocos; i++) {
    blocos.push({
      sequencia: i + 1,
      totalBlocos,
      registros: registros.slice(i * tamanhoBloco, (i + 1) * tamanhoBloco),
    });
  }
  return blocos;
}
