## ADDED Requirements

### Requirement: Vencimento nominal da NTN-B em `base + vVertcCurva`
O modelo `NTNB_BOOTSTRAP_ANBIMA` SHALL tomar o vencimento nominal `V` de cada título como `dataBase + vVertcCurva` (dias corridos), **sem forçar o dia do mês**. As datas nominais de evento SHALL ser `V`, `V − 6 meses`, `V − 12 meses` e assim por diante, e só entram os eventos com data de pagamento posterior à data-base. Cada data de pagamento SHALL ser a data nominal ajustada por `Following` no calendário cadastrado; o dia útil do evento SHALL ser contado até a data de pagamento. O modelo MUST NOT trocar o dia da data nominal por 15 nem por qualquer outro.

#### Scenario: Vencimento no dia 15 em dia não útil
- **WHEN** a NTN-B com `vVertcCurva` = `229` é construída com data-base `2026-09-28`
- **THEN** `V` = `2027-05-15` (sábado), a data de pagamento do vencimento é `2027-05-17` (segunda-feira, por `Following`), e o evento anterior tem data nominal `2026-11-15` (domingo) e pagamento em `2026-11-16`

#### Scenario: Vencimento fora do dia 15
- **WHEN** um título com `vVertcCurva` = `232` é construído com data-base `2026-09-28`
- **THEN** `V` = `2027-05-18` e as datas nominais de evento são `2027-05-18`, `2026-11-18` e as anteriores a elas, todas no dia 18; o modelo não as move para o dia 15

#### Scenario: Prazo não positivo
- **WHEN** o título chega com `vVertcCurva` menor ou igual a zero
- **THEN** o título é descartado como já é hoje (prazo não positivo), sem tentar corrigir a data

### Requirement: Resultado igual para a carga real da ANBIMA
Para a carga real da ANBIMA (NTN-B com `Codigo SELIC` `760199` e vencimento sempre no dia 15, `prazoDiasCorridos = vencimento − dataBase`), a curva construída SHALL ser a mesma que o modelo produzia antes desta mudança, porque `dataBase + vVertcCurva` já é o dia 15.

#### Scenario: Carga de 13/10/2026
- **WHEN** a curva NTN-B de `2026-10-13` é construída com as 14 NTN-B do arquivo `ms261013.txt` (vencimentos de `2027-05-15` a `2060-08-15`)
- **THEN** os valores de cada vértice coincidem com os da versão anterior do modelo, na precisão arredondada da curva
