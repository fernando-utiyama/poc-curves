## Purpose

Constrói a curva NTN-B de taxa zero real a partir das taxas indicativas por título publicadas pela ANBIMA, gravadas em `tAnbmaCurvaPrimr`, por bootstrap sequencial. Fixa a leitura da tabela, o fluxo de caixa, a cotação, a regra de desconto dos cupons, a busca da raiz e os erros.

## ADDED Requirements

### Requirement: Leitura dos títulos
O modelo `NTNB_BOOTSTRAP_ANBIMA` SHALL exigir origem com fonte `ANBIMA` e produto `TP`, e interpolador `Linear` ou `LogLinear`; caso contrário, `CADASTRO_INVALIDO`. O modelo SHALL ler as linhas de `tAnbmaCurvaPrimr` com `cTickerIndcd` = código na fonte e `dBaseReft` = data-base `B`. Cada linha é um título:
- `vPrecoTx`: taxa indicativa em percentual ao ano; `y = vPrecoTx / 100`;
- `vVertcCurva`: prazo do título em dias úteis a partir de `B`.

Para cada título:
- data de pagamento do vencimento `P` = `B` avançada `vVertcCurva` dias úteis pelo calendário cadastrado;
- vencimento nominal `V` = dia 15 do mês de `P`.

Regras:
- linha com `vPrecoTx` nulo SHALL ser descartada com o motivo `SEM_TAXA`;
- se não houver linhas, ou todas forem descartadas: `INSUMO_AUSENTE`;
- `vVertcCurva` nulo, menor que 1 ou fracionário: `INSUMO_INVALIDO`;
- se `V` ajustado por `Following` for diferente de `P`: `INSUMO_INVALIDO`, com o motivo "prazo não corresponde a um vencimento de NTN-B (dia 15)";
- dois títulos com o mesmo `V`: `INSUMO_INVALIDO`.

#### Scenario: Vencimento derivado do prazo
- **WHEN** uma linha tem `vVertcCurva` igual à quantidade de dias úteis entre `B` e `2035-05-15` (dia útil)
- **THEN** o título tem `P` = `V` = `2035-05-15`

#### Scenario: Prazo inconsistente
- **WHEN** `vVertcCurva` leva a uma data `P` em que o dia 15 do mês, ajustado, não é `P` (por exemplo, prazo em dias corridos em vez de úteis)
- **THEN** a construção falha com `INSUMO_INVALIDO`, informando a linha, `P` e o motivo

#### Scenario: Título sem taxa
- **WHEN** um dos títulos da data tem `vPrecoTx` nulo
- **THEN** o título é descartado com `SEM_TAXA`, registrado no log e na memória, e a curva é montada com os demais

### Requirement: Fluxo de caixa do título
O cupom da NTN-B SHALL ser fixo no modelo, sem parâmetro de cadastro: 6% a.a. real, pago semestralmente, com valor `c = 100 × (1,06^(1/2) − 1)` por 100 de valor nominal, calculado sem arredondamento. As datas nominais de evento de um título SHALL ser `V`, `V − 6 meses`, `V − 12 meses` e assim por diante. Cada data de pagamento é a data nominal ajustada por `Following` no calendário cadastrado, e só entram os eventos com data de pagamento posterior a `B`. O fluxo SHALL ser `c` em cada evento e `c + 100` no vencimento. `DU_i` é o `DU` da data de pagamento do evento `i`.

#### Scenario: Fluxos de um título
- **WHEN** um título vence em `2028-08-15` e `B` = `2026-09-14`
- **THEN** os eventos são pagos em `2027-02-15`, `2027-08-16` (15 de agosto de 2027 é domingo), `2028-02-15` e `2028-08-15`, com fluxo `c` nos três primeiros e `c + 100` no último

### Requirement: Cotação a partir da taxa indicativa
A cotação de cada título SHALL ser `C = Σ F_i × (1 + y)^(−DU_i/252)` sobre todos os eventos do título, sem arredondamento.

#### Scenario: Cotação reproduzível
- **WHEN** a cotação de um título é calculada duas vezes com os mesmos dados
- **THEN** os dois valores são idênticos

### Requirement: Bootstrap sequencial
Os títulos SHALL ser resolvidos em ordem crescente de `V`. Resolver o título `n` é encontrar a taxa zero decimal `z_n` tal que `f(z) = Σ F_i × DF_i(z) − C = 0`, com `DF_n(z) = (1 + z)^(−DU_n/252)` no vencimento e, para cada evento `i` anterior ao vencimento:
- **`INCOGNITA`**: se nenhum título foi resolvido ainda (primeiro título), `DF_i = (1 + z)^(−DU_i/252)`;
- **`FLAT_INICIO`**: se a data do evento for anterior à do primeiro título resolvido, `DF_i = (1 + z_1)^(−DU_i/252)`;
- **`RESOLVIDO`**: se a data do evento for igual à de um título resolvido `k`, `DF_i = (1 + z_k)^(−DU_i/252)`;
- **`INTERPOLADO`**: nos demais casos, o `DF` interpolado pela grandeza, pelo interpolador e pelo eixo cadastrados, entre os dois pontos vizinhos no conjunto formado pelos títulos já resolvidos e pelo ponto do próprio título `(P_n, z)`.

Durante o bootstrap, as taxas `z_k` SHALL ser usadas sem arredondamento. A raiz SHALL ser encontrada por bisseção no intervalo `[−0,99; 1,00]`. Se `f` não trocar de sinal nos extremos, a construção falha com `MODELO_FALHOU`, informando o título. Senão, a bisseção repete até a largura do intervalo ser menor que `10^−14` ou até 200 iterações, e `z_n` é o ponto médio do intervalo final. Cada título gera um ponto: data = `P_n`, valor = `z_n × 100`.

#### Scenario: Primeiro título
- **WHEN** o título de menor vencimento é resolvido
- **THEN** todos os seus eventos são descontados pela própria incógnita, e `z_1` é igual a `y_1` dentro da tolerância da bisseção

#### Scenario: Cupom em data de título resolvido
- **WHEN** um título paga cupom na data de pagamento do vencimento de um título mais curto já resolvido
- **THEN** esse cupom é descontado por `z_k` daquele título, com origem `RESOLVIDO`

#### Scenario: Cupom entre dois títulos resolvidos
- **WHEN** um título paga cupom entre os vencimentos de dois títulos resolvidos
- **THEN** o `DF` desse cupom é interpolado entre os dois pontos pela interpolação cadastrada, com origem `INTERPOLADO`

#### Scenario: Sem troca de sinal
- **WHEN** a taxa indicativa de um título é incompatível com os títulos anteriores e `f` não troca de sinal em `[−0,99; 1,00]`
- **THEN** a construção falha com `MODELO_FALHOU`, informando o vencimento do título e os valores de `f` nos extremos

### Requirement: Pontos em datas reais de vencimento
Os pontos da `NTN-B` SHALL estar nas datas de pagamento dos vencimentos dos títulos lidos, não numa grade padronizada. A quantidade de pontos SHALL ser igual à de títulos não descartados.

#### Scenario: Quantidade de pontos
- **WHEN** a tabela tem 14 títulos na data e um deles não tem taxa
- **THEN** a curva tem 13 pontos

### Requirement: Memória de cálculo do modelo
O modelo SHALL registrar na memória de cálculo: na aba `Insumos`, a tabela `tAnbmaCurvaPrimr` e as colunas lidas `cTickerIndcd`, `dBaseReft`, `vVertcCurva`, `vPrecoTx`; na aba `Pontos`, as colunas extras `Vencimento nominal`, `Taxa indicativa`, `Cotacao`, `Iteracoes` e `Residuo` (`f(z_n)`). A aba `Fluxos` SHALL ter uma linha por evento de cada título, com as colunas `Titulo` (vencimento nominal), `Data nominal`, `Data pagamento`, `DU`, `Fluxo`, `Origem DF`, `DF` (calculado com o `z_n` final) e `Valor presente`.

#### Scenario: Fluxos na planilha
- **WHEN** a `NTN-B` de uma data com 13 títulos é simulada
- **THEN** a aba `Fluxos` tem uma linha por evento de cada título, e a soma de `Valor presente` de cada título é igual à `Cotacao` desse título, a menos do `Residuo`

### Requirement: Cadastro da NTN-B
A curva SHALL ser cadastrada com:
- código `NTNB`, nome `NTN-B`, origem `ANBIMA`/`TP`/`NTN-B`;
- construção `NTNB_BOOTSTRAP_ANBIMA`, unidade `TAXA`;
- `Discount` + `LogLinear`, eixo `Business252`, cotação `Business252`/`Compounded`/`Annual`;
- calendário `Brazil`/`Settlement`/`Following`;
- extrapolação de início `FlatValue`: o primeiro vencimento pode estar a meses da data-base, e o bootstrap já trata esse trecho com a primeira taxa zero;
- extrapolação de fim `FlatForward`;
- horizonte `10Y`, 8 casas `HALF_UP` (resultado de cálculo: guardar mais casas que a taxa indicativa evita perder precisão na interpolação).

#### Scenario: Prazo antes do primeiro título
- **WHEN** é pedido o prazo de 21 dias úteis e o primeiro título vence depois disso
- **THEN** o valor é o do primeiro ponto, com classificação `EXTRAPOLADO_INICIO`

### Requirement: Determinismo do bootstrap
Com as mesmas linhas de entrada e o mesmo cadastro, o bootstrap MUST produzir exatamente os mesmos pontos.

#### Scenario: Duas construções idênticas
- **WHEN** a `NTN-B` da mesma data é construída duas vezes sem mudança de insumo ou de cadastro
- **THEN** o `hashPontos` das duas construções é igual
