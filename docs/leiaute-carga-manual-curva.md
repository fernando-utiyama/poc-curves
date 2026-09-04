# Leiaute do arquivo de carga manual de curva

Formato do arquivo de carga manual de vértices de curva (`services/curve-processor`,
seção 6 do backlog — origem `CARREGADA`, tabela `versao_curva`). Suportado em
dois containers equivalentes: CSV e planilha (XLSX) — ambos produzem a mesma
lista de vértices a partir das mesmas colunas, na mesma ordem.

## Colunas (nesta ordem, com cabeçalho)

| Coluna | Obrigatória | Tipo | Observação |
|---|---|---|---|
| `prazo_dias_uteis` | sim | inteiro >= 0 | |
| `prazo_dias_corridos` | não | inteiro >= 0 | célula/campo vazio = nulo |
| `data_vencimento` | não | data `YYYY-MM-DD` | célula/campo vazio = nulo |
| `taxa` | sim | decimal | separador decimal declarado explicitamente por quem chama o leitor — nunca assumido |
| `fator_desconto` | não | decimal | célula/campo vazio = nulo; mesmo separador decimal de `taxa` |

## CSV

- Separador de coluna: declarado por quem chama o leitor (não fixo no formato —
  arquivos diferentes podem usar `;` ou `,`), nunca inferido automaticamente.
- Separador decimal: declarado por quem chama o leitor, independente do
  separador de coluna (podem ser iguais só se o separador de coluna não for
  vírgula).
- Primeira linha é sempre o cabeçalho, com exatamente os 5 nomes de coluna
  acima, na ordem acima — cabeçalho divergente é recusado (tarefa 6.5).
- Encoding declarado por quem chama o leitor (mesmo padrão do resto do
  serviço — nunca assumido).

## Planilha (XLSX)

- Mesma ordem de colunas, mesmo cabeçalho, na primeira aba, primeira linha.
- Células vazias equivalem a coluna ausente no CSV (nula).
- `taxa`/`fator_desconto` podem estar como célula numérica (lida diretamente,
  sem conversão de texto) ou como texto (mesma regra de separador decimal
  declarado do CSV, quando a célula é texto).

## Regras comuns aos dois formatos

- Nenhum valor numérico é arredondado ou convertido para `double` em
  nenhuma etapa da leitura (D8 do design.md) — direto para `BigDecimal`.
- Todos os erros de todas as linhas são coletados antes de decidir
  aceitar ou recusar o arquivo — nunca aplicação parcial (tarefa 6.4).
- Arquivo vazio (sem nenhuma linha de dado além do cabeçalho) e prazo
  duplicado (`prazo_dias_uteis` repetido) são recusados (tarefa 6.5).
