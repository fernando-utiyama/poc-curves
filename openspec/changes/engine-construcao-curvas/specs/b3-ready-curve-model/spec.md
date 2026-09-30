## Purpose

Constrói as curvas B3 do primeiro objetivo (PRE, DCL, PTX, DPL e INP) a partir dos vértices prontos do arquivo Taxas de Mercado para Swaps (`TaxaSwap.txt`), gravados em `tBtrsCurvaPrimr`, sem reimplementar a metodologia da B3. Os valores de referência desta spec vêm do `TaxaSwap.txt` de `2026-09-14`.

## ADDED Requirements

### Requirement: Leitura dos vértices prontos
O modelo `PRONTA_TS_B3` SHALL exigir origem com fonte `B3` e produto `TS`; outra origem MUST resultar em `CADASTRO_INVALIDO`. O modelo SHALL ler as linhas de `tBtrsCurvaPrimr` com `cTickerIndcd` = nome da curva (o processor grava os vértices sob a curva de mercado ligada ao código na fonte em `tCurvaPrvdr`) e `dBaseReft` = data-base, e gerar um ponto por linha:
- data do ponto = data-base + `cDiaCorri` dias corridos;
- valor = `vPrecoTx`, sem alteração de sinal nem de escala;
- dias úteis publicados = `cDiaUtil`, obedecidos (spec `curve-build-pipeline`, requisito "Dias úteis publicados pela fonte ou informados pelo usuário").

`vFatorAcum` e `vFatorDia` MUST ser ignorados. O código da curva MUST NOT estar fixo no modelo: vem sempre da origem cadastrada.

#### Scenario: Primeiro e último vértice da PRE
- **WHEN** a `PRE` de `2026-09-14` é construída
- **THEN** o primeiro ponto é `2026-09-15` com valor calculado 13,9000000, gravado como 13,9000000, e o último é `2060-08-16`, gravado como 14,1600000

#### Scenario: Valor negativo preservado
- **WHEN** a `DCL` de `2026-09-14` é construída
- **THEN** o primeiro ponto é `2026-09-15` com valor -117,9600000

#### Scenario: Nome da curva diferente do código na fonte
- **WHEN** uma curva `DI_MERCADO` é cadastrada com origem `B3`/`TS`/`PRE`, e o processor gravou os vértices do `PRE` também sob `DI_MERCADO`
- **THEN** `DI_MERCADO` é construída com os mesmos pontos da `DIxPRE`

### Requirement: Regras do arquivo
O `TaxaSwap.txt` já chega validado pelo leiaute no processor (change `conector-b3-webhook-ingest`), que não grava o código com linha inválida; as regras abaixo protegem contra dado alterado depois da gravação. Nenhuma linha SHALL ser descartada. Para cada situação das linhas lidas de `tBtrsCurvaPrimr`, o resultado SHALL ser:

| Situação | Resultado |
|---|---|
| nenhuma linha da curva na data | falha: `INSUMO_AUSENTE` |
| `cDiaCorri` nulo ou menor que 1 | falha: `INSUMO_INVALIDO` (linha) |
| `cDiaUtil` nulo, menor que 1 ou maior que `cDiaCorri` | falha: `INSUMO_INVALIDO` (linha) |
| `vPrecoTx` nulo | falha: `INSUMO_INVALIDO` (linha) |
| duas linhas com o mesmo `cDiaCorri` (duas taxas para a mesma data) | falha: `INSUMO_INVALIDO` (as duas linhas) |
| `cDiaUtil` diferente do `DU` do calendário, ou data do ponto em dia não útil | constrói com os dias publicados e o aviso `CALENDARIO_DIVERGENTE` (calendário desatualizado: um feriado ausente ou a mais muda a contagem) |
| taxa negativa | constrói, sem aviso (cupons têm taxa negativa) |

#### Scenario: Calendário divergente
- **WHEN** o calendário `Brazil` não tem um feriado que a B3 considerou, e por isso o `DU` calculado de um vértice difere de `cDiaUtil`
- **THEN** a curva é construída com o `cDiaUtil` publicado, com o aviso `CALENDARIO_DIVERGENTE` informando o vértice, o `DU` calculado e o `cDiaUtil` publicado

### Requirement: Cadastro das cinco curvas
As cinco curvas SHALL ser cadastradas com construção `PRONTA_TS_B3`, origem `B3`/`TS`, calendário `Brazil`/`Settlement`/`Following`, extrapolação de início `Disabled` e horizonte `10Y`, com os demais itens abaixo. Eles reproduzem o Manual de Curvas B3. As casas decimais são as 7 do leiaute oficial do `TaxaSwap.txt` (campo "Taxa teórica", posições 53 a 66), e não as observadas num arquivo: o valor gravado é sempre idêntico ao publicado.

| Código | Nome | Código na fonte | Unidade | Base de interpolação + interpolador | Eixo | Cotação | Extrap. fim | Casas | Modo |
|---|---|---|---|---|---|---|---|---|---|
| `PRE` | DIxPRE | `PRE` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252`/`Compounded`/`Annual` | `FlatForward` | 7 | `HALF_UP` |
| `DCL` | Cupom limpo de dólar | `DCL` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Actual360`/`Simple` | `FlatForward` | 7 | `HALF_UP` |
| `DPL` | Cupom Limpo DI X IPCA | `DPL` | `TAXA` | `Discount` + `LogLinear` | `Business252` | `Business252`/`Compounded`/`Annual` | `FlatForward` | 7 | `HALF_UP` |
| `INP` | IBOVESPA | `INP` | `PONTOS` | `Price` + `LogLinear` | `Business252` | — | `FlatValue` | 7 | `HALF_UP` |
| `PTX` | PTAX - USD | `PTX` | `PRECO` | `Price` + `LogLinear` | `Business252` | — | `Disabled` | 7 | `DOWN` |

#### Scenario: Interpolação da DCL
- **WHEN** a `DCL` é interpolada entre dois pontos
- **THEN** o valor é o da fórmula 1.4.3 do manual (Flat Forward 252 com convenção linear)

#### Scenario: Ponto preservado
- **WHEN** é pedido o prazo de 7.406 dias úteis da `PRE` de `2026-09-14`, que é o ponto `2056-04-10`, publicado com 14,1670000
- **THEN** o valor devolvido é 14,1670000, com classificação `PONTO`

#### Scenario: PTX além do último ponto
- **WHEN** é pedido um prazo da `PTX` depois de `2060-08-16`
- **THEN** a consulta falha com `PRAZO_FORA_DO_DOMINIO`, porque a extrapolação de fim é `Disabled`

### Requirement: Oráculo contra o arquivo publicado
Para cada uma das cinco curvas, cada vértice e cada arquivo da massa de regressão, a interpolação do prazo do vértice SHALL devolver exatamente `vPrecoTx`, e a construção MUST NOT trazer `CALENDARIO_DIVERGENTE`: o calendário `Brazil` SHALL contar exatamente o `cDiaUtil` publicado. O oráculo é o teste do calendário; em produção, a divergência vira aviso. A massa de regressão SHALL ter o `TaxaSwap.txt` de todos os pregões de pelo menos 12 meses consecutivos, cobrindo obrigatoriamente Carnaval, Sexta-feira Santa, Corpus Christi, virada de ano e o 20 de novembro, além do arquivo de `2026-09-14`. Toda data que apresentar divergência em produção SHALL ser acrescentada à massa.

#### Scenario: Pregão antes do Carnaval
- **WHEN** o oráculo roda sobre o arquivo do último pregão antes do Carnaval
- **THEN** o `DU` de todos os vértices bate com o publicado, contando segunda e terça de Carnaval como não úteis

#### Scenario: Oráculo da PTX
- **WHEN** os 278 vértices da `PTX` de `2026-09-14` são consultados pelos seus prazos
- **THEN** cada valor devolvido é igual ao publicado, com 7 casas
