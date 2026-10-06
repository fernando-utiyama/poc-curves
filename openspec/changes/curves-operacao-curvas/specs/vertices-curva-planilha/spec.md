## Purpose

No `services/curves`, exportar os vértices de várias curvas e datas-base para uma planilha e importá-la de volta, editada, para substituir vértices em lote. A importação pode ser simulada, mostrando vértice a vértice o que vai mudar, e é aplicada inteira numa transação ou não é aplicada. Segue as regras de validação e de preferência da spec `vertices-curva-manual`.

## ADDED Requirements

### Requirement: Estrutura da planilha de vértices
A planilha (`.xlsx`) SHALL ter uma única aba, `Vertices`, com o cabeçalho na primeira linha e exatamente cinco colunas, nesta ordem: `Curva`, `DataBase`, `DataVertice`, `Valor`, `DiasUteis`. `DiasUteis` é opcional por linha: célula vazia significa sem dias úteis informados, e o engine usa o calendário; preenchida, é obedecida, como `diasUteis` do `PUT` (spec `vertices-curva-manual`). Na exportação, `DiasUteis` sai com os dias úteis gravados do vértice (`tDadoVertcCurva.cDiaUtil`), de modo que exportar e reimportar sem edição não muda nada. Cada linha é um vértice, e a exportação as ordena por curva, data-base e data do vértice. `Curva` SHALL ser o nome da curva (`tCurvaMercd.cTickerIndcd`), que é imutável, como na planilha de cadastro. Na importação, a ordem das linhas não importa, e colunas além das cinco (como a `Resultado` de uma simulação anterior) SHALL ser ignoradas. Uma planilha sem a coluna `DiasUteis` SHALL ser aceita como se todas as células dela estivessem vazias. `DiasUteis` SHALL ser célula numérica inteira ou texto com um inteiro; outro conteúdo é erro na linha.

`DataBase` e `DataVertice` SHALL ser células de data. Como o front e os usuários são pt-BR, as datas SHALL ser células de data com o formato de exibição `dd/mm/aaaa`, e os números, células numéricas (o Excel em pt-BR mostra a vírgula decimal). Na importação, uma data em texto SHALL ser aceita como `dd/mm/aaaa` ou `aaaa-mm-dd`, e um número em texto, com vírgula ou ponto decimal e sem separador de milhar (`13,9000000` ou `13.9000000`); texto com vírgula e ponto ao mesmo tempo MUST ser recusado, por ser ambíguo. Nomes de abas e de colunas ficam sem acento. `Valor` SHALL ser célula numérica quando o valor tiver até 15 dígitos significativos (limite do Excel), e texto caso contrário, com vírgula decimal (pt-BR), para não perder precisão; na importação, os dois formatos são aceitos.

#### Scenario: Valor com 7 casas
- **WHEN** os vértices da `PTX` são exportados
- **THEN** o valor 56,3772259 aparece como célula numérica com as 7 casas, sem arredondamento

### Requirement: Exportação dos vértices
`GET /api/v1/curvas-mercado/vertices/exportacao?codigos=PRE,DCL&de=AAAA-MM-DD&ate=AAAA-MM-DD` SHALL devolver a planilha com os vértices gravados das curvas pedidas (até 50 códigos) nas datas-base do intervalo (até 366 dias), limitada a 100.000 vértices; acima disso, 400 `PARAMETRO_INVALIDO`. O arquivo SHALL se chamar `pontos-curvas_{AAAAMMDDHHmmss}.xlsx`, no horário de Brasília.

#### Scenario: Exportar uma semana de duas curvas
- **WHEN** o gestor exporta `PRE` e `DCL` de `2026-09-14` a `2026-09-18`
- **THEN** a aba `Vertices` tem uma linha por vértice das duas curvas, em cada data-base com vértices no intervalo, com as colunas `Curva`, `DataBase`, `DataVertice`, `Valor` e `DiasUteis` (os dias úteis publicados pela B3)

### Requirement: Semântica da importação de vértices
`POST /api/v1/curvas-mercado/vertices/importacao?modo=SIMULACAO|APLICACAO` (`multipart/form-data`, campo `arquivo`, até 10 MB e 100.000 vértices) SHALL tratar, para cada par (`Curva`, `DataBase`) presente na aba `Vertices`, as linhas desse par como a **lista completa** de vértices da data-base, com o mesmo efeito do `PUT` da spec `vertices-curva-manual`. Pares ausentes da planilha MUST NOT ser tocados. Não há exclusão de uma data-base inteira pela planilha: usa-se o `DELETE`. Cada par SHALL passar pelas validações e pelo arredondamento da spec `vertices-curva-manual`, inclusive a checagem de feriados: só as recusas por consistência de banco impedem o lote, e os avisos de regra de negócio (`PONTO_EM_FERIADO`, `PONTO_EM_FIM_DE_SEMANA` e os demais) aparecem na linha do vértice, sem impedir nada. Depois da aplicação, os vértices de cada par presente na planilha SHALL ser exatamente os da planilha (com o arredondamento avisado): o que o gestor vê na planilha é o que fica gravado e o que o engine usa. Importar a planilha exportada sem edição MUST resultar em zero mudanças e nenhuma escrita.

#### Scenario: Dias úteis apagados na planilha
- **WHEN** o gestor apaga a célula `DiasUteis` de um vértice e importa
- **THEN** o vértice passa a não ter dias úteis informados, a simulação mostra `ALTERACAO` nessa linha, e o engine passa a usar o calendário para ele

#### Scenario: Correção de um vértice em várias datas
- **WHEN** o gestor corrige o vértice de 252 dias úteis da `DCL` em 5 datas-base e importa a planilha
- **THEN** só esses 5 vértices mudam, e os demais vértices das 5 datas-base continuam iguais

### Requirement: Simulação e aplicação dos vértices
Com `modo=SIMULACAO`, o serviço SHALL validar tudo e devolver, sem gravar, as mudanças por vértice (`INCLUSAO`, `ALTERACAO` com valor atual e novo, `EXCLUSAO`), o valor que será gravado em cada linha (já arredondado), os erros (aba, linha, coluna, motivo) e os avisos. Com `modo=APLICACAO`, SHALL aplicar todos os pares numa única transação, travando as curvas envolvidas em ordem alfabética de nome (para não haver deadlock com o engine nem com outra importação) e esperando as travas dentro do tempo limite da requisição (120 segundos). Cada par SHALL ser gravado pela diferença e conferido pelo `hashPontos` relido, como no `PUT`; uma conferência que falhe desfaz o lote inteiro. Se houver qualquer recusa por consistência de banco, MUST NOT aplicar nada e SHALL responder 422 com os mesmos erros da simulação. Não há conferência de versão: vale a preferência da edição manual. Cada par alterado SHALL gerar o log `VERTICES_EDITADOS` com origem `PLANILHA` e o `idLote` (UUID) do lote. Depois do commit, o serviço SHALL pedir ao engine a regravação da curva interpolada de cada par presente na planilha, como no `PUT`, uma chamada por par; cada falha vira o aviso `INTERPOLADA_DESATUALIZADA` nas linhas do par, sem desfazer o lote. Em qualquer dos modos, `formato=xlsx` SHALL devolver a própria planilha com a coluna `Resultado` em cada linha (`SEM_MUDANCA`, `INCLUSAO`, `ALTERACAO` ou o erro, e os avisos), uma coluna `ValorGravado` com o valor final de cada linha e uma aba `Resumo`, e os vértices que serão excluídos listados numa aba `Exclusoes`.

#### Scenario: Simular antes de aplicar
- **WHEN** a planilha com 5 vértices corrigidos é enviada com `modo=SIMULACAO&formato=xlsx`
- **THEN** a planilha volta com `ALTERACAO` nas 5 linhas e `SEM_MUDANCA` nas demais, e nada é gravado

#### Scenario: Um erro impede o lote
- **WHEN** a planilha tem a mesma data duas vezes para a `PRE` numa data-base e é enviada com `modo=APLICACAO`
- **THEN** a resposta é 422 citando a aba, a linha e a data, e nenhum vértice do lote é gravado

#### Scenario: Aviso não impede o lote
- **WHEN** um dos vértices da planilha cai num sábado e ela é enviada com `modo=APLICACAO`
- **THEN** o lote é gravado, e a linha do vértice traz o aviso `PONTO_EM_FIM_DE_SEMANA`
