## Purpose

No `services/curves`, exportar os pontos de várias curvas e datas-base para uma planilha e importá-la de volta, editada, para substituir pontos em lote. A importação pode ser simulada, mostrando ponto a ponto o que vai mudar, e é aplicada inteira numa transação ou não é aplicada. Segue as regras de validação e de preferência da spec `pontos-curva-manual`.

## ADDED Requirements

### Requirement: Estrutura da planilha de pontos
A planilha (`.xlsx`) SHALL ter uma única aba, `Pontos`, com o cabeçalho na primeira linha e exatamente quatro colunas, nesta ordem: `Curva`, `DataBase`, `DataPonto`, `Valor`. Cada linha é um ponto, e a exportação as ordena por curva, data-base e data do ponto. `Curva` SHALL ser o nome da curva (`tCurvaMercd.cTickerIndcd`), que é imutável, como na planilha de cadastro. Na importação, a ordem das linhas não importa, e colunas além das quatro (como a `Resultado` de uma simulação anterior) SHALL ser ignoradas.

`DataBase` e `DataPonto` SHALL ser células de data (`aaaa-mm-dd`). `Valor` SHALL ser célula numérica quando o valor tiver até 15 dígitos significativos (limite do Excel), e texto caso contrário, para não perder precisão; na importação, os dois formatos são aceitos.

#### Scenario: Valor com 7 casas
- **WHEN** os pontos da `PTX` são exportados
- **THEN** o valor 56,3772259 aparece como célula numérica com as 7 casas, sem arredondamento

### Requirement: Exportação dos pontos
`GET /api/v1/curvas-mercado/pontos/exportacao?codigos=PRE,DCL&de=AAAA-MM-DD&ate=AAAA-MM-DD` (papel `Curvas.Leitura`) SHALL devolver a planilha com os pontos gravados das curvas pedidas (até 50 códigos) nas datas-base do intervalo (até 366 dias), limitada a 100.000 pontos; acima disso, 400 `PARAMETRO_INVALIDO`. O arquivo SHALL se chamar `pontos-curvas_{AAAAMMDDHHmmss}.xlsx`, no horário de Brasília.

#### Scenario: Exportar uma semana de duas curvas
- **WHEN** o gestor exporta `PRE` e `DCL` de `2026-09-14` a `2026-09-18`
- **THEN** a aba `Pontos` tem uma linha por ponto das duas curvas, em cada data-base com pontos no intervalo, com as colunas `Curva`, `DataBase`, `DataPonto` e `Valor`

### Requirement: Semântica da importação de pontos
`POST /api/v1/curvas-mercado/pontos/importacao?modo=SIMULACAO|APLICACAO` (papel `Curvas.Operador`, `multipart/form-data`, campo `arquivo`, até 10 MB e 100.000 pontos) SHALL tratar, para cada par (`Curva`, `DataBase`) presente na aba `Pontos`, as linhas desse par como a **lista completa** de pontos da data-base, com o mesmo efeito do `PUT` da spec `pontos-curva-manual`. Pares ausentes da planilha MUST NOT ser tocados. Não há exclusão de uma data-base inteira pela planilha: usa-se o `DELETE`. Cada par SHALL passar pelas validações e pelo arredondamento da spec `pontos-curva-manual`, inclusive a checagem de feriados: só as recusas por consistência de banco impedem o lote, e os avisos de regra de negócio (`PONTO_EM_FERIADO`, `PONTO_EM_FIM_DE_SEMANA` e os demais) aparecem na linha do ponto, sem impedir nada. Depois da aplicação, os pontos de cada par presente na planilha SHALL ser exatamente os da planilha (com o arredondamento avisado): o que o gestor vê na planilha é o que fica gravado e o que o engine usa. Importar a planilha exportada sem edição MUST resultar em zero mudanças e nenhuma escrita.

#### Scenario: Correção de um vértice em várias datas
- **WHEN** o gestor corrige o vértice de 252 dias úteis da `DCL` em 5 datas-base e importa a planilha
- **THEN** só esses 5 pontos mudam, e os demais pontos das 5 datas-base continuam iguais

### Requirement: Simulação e aplicação dos pontos
Com `modo=SIMULACAO`, o serviço SHALL validar tudo e devolver, sem gravar, as mudanças por ponto (`INCLUSAO`, `ALTERACAO` com valor atual e novo, `EXCLUSAO`), o valor que será gravado em cada linha (já arredondado), os erros (aba, linha, coluna, motivo) e os avisos. Com `modo=APLICACAO`, SHALL aplicar todos os pares numa única transação, travando as curvas envolvidas em ordem alfabética de nome (para não haver deadlock com o engine nem com outra importação) e esperando as travas dentro do tempo limite da requisição (120 segundos). Cada par SHALL ser gravado pela diferença e conferido pelo `hashPontos` relido, como no `PUT`; uma conferência que falhe desfaz o lote inteiro. Se houver qualquer recusa por consistência de banco, MUST NOT aplicar nada e SHALL responder 422 com os mesmos erros da simulação. Não há conferência de versão: vale a preferência da edição manual. Cada par alterado SHALL gerar o log `PONTOS_EDITADOS` com origem `PLANILHA` e o `idLote` (UUID) do lote. Em qualquer dos modos, `formato=xlsx` SHALL devolver a própria planilha com a coluna `Resultado` em cada linha (`SEM_MUDANCA`, `INCLUSAO`, `ALTERACAO` ou o erro, e os avisos), uma coluna `ValorGravado` com o valor final de cada linha e uma aba `Resumo`, e os pontos que serão excluídos listados numa aba `Exclusoes`.

#### Scenario: Simular antes de aplicar
- **WHEN** a planilha com 5 pontos corrigidos é enviada com `modo=SIMULACAO&formato=xlsx`
- **THEN** a planilha volta com `ALTERACAO` nas 5 linhas e `SEM_MUDANCA` nas demais, e nada é gravado

#### Scenario: Um erro impede o lote
- **WHEN** a planilha tem a mesma data duas vezes para a `PRE` numa data-base e é enviada com `modo=APLICACAO`
- **THEN** a resposta é 422 citando a aba, a linha e a data, e nenhum ponto do lote é gravado

#### Scenario: Aviso não impede o lote
- **WHEN** um dos pontos da planilha cai num sábado e ela é enviada com `modo=APLICACAO`
- **THEN** o lote é gravado, e a linha do ponto traz o aviso `PONTO_EM_FIM_DE_SEMANA`
