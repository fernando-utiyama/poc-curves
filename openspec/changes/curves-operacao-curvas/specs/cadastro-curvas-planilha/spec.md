## Purpose

No `services/curves`, exportar o cadastro de curvas (curvas, provedores da curva e configurações) para uma planilha e importá-la de volta, editada, para alterar várias curvas de uma vez. A importação pode ser simulada, mostrando exatamente o que vai mudar, e é aplicada inteira numa transação ou não é aplicada.

## ADDED Requirements

### Requirement: Estrutura da planilha
A planilha (`.xlsx`) SHALL ter as abas abaixo, nesta ordem, com o cabeçalho na primeira linha, exatamente com estes nomes de coluna. Em todas as abas, a curva é identificada pelo **nome** (imutável), na coluna `Curva`.

1. **`Curvas`**: `Curva`, `Codigo`, `Unidade`, `DayCounterCotacao`, `Compounding`, `Moeda`, `Pais`, `Classificacao`, `ClasseAtivo`, `Situacao`, `InicioVigencia`, `FimVigencia`.
2. **`Provedores`**: `Curva`, `Provedor`, `Produto`, `CodigoNaFonte`, `Prioridade`.
3. **`Configuracoes`**: `Curva`, `Versao`, `InicioVigencia`, `FimVigencia`, `ModeloConstrucao`, `Interpolador` e uma coluna por chave de parâmetro da spec `configuracao-calculo-curva`, com o nome exato da chave (`BASE_INTERPOLACAO`, `DAY_COUNTER_TEMPO` e as demais). `MODELOS_POR_ORIGEM` é uma célula de texto no formato `{provedor}/{produto}={modelo}`, com as entradas separadas por `;` e sem espaços (ex.: `B3/TS=PRONTA_TS_B3;BLOOMBERG/BLC2=SOFR_ZERO_BLOOMBERG`); célula vazia significa sem a chave. Na exportação, as entradas saem em ordem alfabética da chave, para que a planilha exportada e reimportada não gere alteração.
4. **`Valores`**: só informativa, gerada de `GET /api/v1/curvas-mercado/valores` (spec `configuracao-calculo-curva`), com os valores aceitos de cada campo e a descrição de cada um; ignorada na importação.

As colunas de valor fechado (`Unidade`, `DayCounterCotacao`, `Compounding`, `Situacao`, `Provedor` e as chaves de parâmetro com lista) SHALL ter validação de dados do Excel com lista suspensa restritiva, apontando para a aba `Valores`. `ModeloConstrucao`, `Interpolador` e `CALENDARIO` SHALL ter lista suspensa só com aviso, que aceita outro nome, porque um script Groovy pode criá-lo depois. A importação continua validando tudo, com ou sem a validação do Excel.

Datas SHALL ser células de data, números SHALL ser células numéricas, e célula vazia significa campo nulo. Como o front e os usuários são pt-BR, as datas SHALL ser células de data com o formato de exibição `dd/mm/aaaa`, e os números, células numéricas (o Excel em pt-BR mostra a vírgula decimal). Na importação, uma data em texto SHALL ser aceita como `dd/mm/aaaa` ou `aaaa-mm-dd`, e um número em texto, com vírgula ou ponto decimal e sem separador de milhar (`13,9000000` ou `13.9000000`); texto com vírgula e ponto ao mesmo tempo MUST ser recusado, por ser ambíguo. Nomes de abas e de colunas ficam sem acento.

#### Scenario: Modelo por origem na planilha
- **WHEN** a linha de uma versão nova da `DI_BACKUP` tem `MODELOS_POR_ORIGEM` = `B3/TS=PRONTA_TS_B3`
- **THEN** a versão é criada com `{"B3/TS":"PRONTA_TS_B3"}` em `cModDado`; uma célula `B3/TS` sem `=` é erro na linha e na coluna

#### Scenario: Colunas da configuração
- **WHEN** o cadastro é exportado
- **THEN** a aba `Configuracoes` tem uma coluna para cada chave de parâmetro, e cada versão ocupa uma linha, com o valor de cada parâmetro na sua coluna

### Requirement: Exportação
`GET /api/v1/curvas-mercado/exportacao?nome=&codigo=&situacao=` (papel `Curvas.Leitura`) SHALL devolver a planilha com as curvas filtradas (todas, sem filtro), com todos os seus provedores e todas as versões de configuração, passadas incluídas. O nome do arquivo SHALL ser `cadastro-curvas_{AAAAMMDDHHmmss}.xlsx`, no horário de Brasília.

#### Scenario: Exportar uma curva
- **WHEN** o cliente exporta com `codigo=PRE`
- **THEN** a planilha tem uma linha em `Curvas` para `DIxPRE`, os provedores da curva dela em `Provedores` e todas as versões dela em `Configuracoes`

### Requirement: Semântica da importação
`POST /api/v1/curvas-mercado/importacao?modo=SIMULACAO|APLICACAO` (papel `Curvas.Cadastro`, `multipart/form-data`, campo `arquivo`, até 5 MB e até 1.000 curvas) SHALL tratar a planilha como o **estado desejado das curvas listadas na aba `Curvas`**:
- **Curvas:** linha com nome inexistente cria a curva; linha com nome existente altera os campos diferentes do atual, inclusive o código. Curvas fora da aba `Curvas` MUST NOT ser tocadas. Não há exclusão de curva: inativa-se pela coluna `Situacao`.
- **Provedores da curva:** para cada curva listada, as linhas de `Provedores` dela são o conjunto completo de provedores da curva. Provedor da curva existente com o mesmo (`Provedor`, `Produto`) é alterado, se `CodigoNaFonte` ou `Prioridade` mudaram; provedor novo é incluído; provedor da curva existente ausente da aba é excluído.
- **Configurações:** linha com `Versao` preenchida MUST ser igual à versão existente em todos os campos (versões não se alteram); linha com `Versao` vazia cria uma versão nova, pelas regras de vigência da spec `configuracao-calculo-curva`; a última versão existente que ainda não começou e está ausente da aba é excluída. Várias versões novas da mesma curva são criadas em ordem de `InicioVigencia`.
- Linha de `Provedores` ou `Configuracoes` de uma curva que não está na aba `Curvas` MUST ser erro.
- `FimVigencia` de `Configuracoes` é só informativa e ignorada.

Todas as regras de campo, unicidade e vigência das specs `cadastro-curva-mercado`, `provedor-curva` e `configuracao-calculo-curva` SHALL valer linha a linha. Importar a planilha exportada sem nenhuma edição MUST resultar em zero alterações.

#### Scenario: Troca de prioridade em lote
- **WHEN** a planilha exportada tem a `Prioridade` de 30 provedores trocada, e é importada
- **THEN** só esses 30 provedores são alterados, e nenhuma outra linha do cadastro muda

#### Scenario: Provedor removido da aba
- **WHEN** a curva `DI_MERCADO` está na aba `Curvas`, mas o seu provedor `B3`/`TS` foi apagado da aba `Provedores`
- **THEN** a importação exclui esse provedor e informa o aviso `CURVA_SEM_ORIGEM`, se ele era o único

#### Scenario: Versão existente editada
- **WHEN** a linha da versão 1 da `PRE` tem o `Interpolador` trocado na planilha
- **THEN** a importação falha nessa linha, informando que versões existentes não se alteram e que é preciso incluir uma linha de versão nova

### Requirement: Simulação e aplicação
Com `modo=SIMULACAO`, o serviço SHALL validar a planilha inteira e devolver, sem gravar nada, a lista de mudanças (aba, linha, curva, tipo `INCLUSAO`, `ALTERACAO` ou `EXCLUSAO`, e, nas alterações, cada campo com o valor atual e o novo), os erros (aba, linha, coluna, motivo) e os avisos. Com `modo=APLICACAO`, SHALL aplicar todas as mudanças numa única transação, com um `idLote` (UUID) comum a todos os eventos `CADASTRO_ALTERADO` do lote. Se houver qualquer erro, MUST NOT aplicar nada e SHALL responder 422 com os mesmos erros da simulação.

Não há conferência de versão: a importação se aplica sobre o estado atual, quem salva por último vence, e o estado anterior de cada mudança fica no `CADASTRO_ALTERADO`.

Em qualquer dos modos, `formato=xlsx` SHALL devolver a própria planilha enviada com a coluna `Resultado` acrescentada em cada aba (`SEM_MUDANCA`, `INCLUSAO`, `ALTERACAO`, `EXCLUSAO` ou o erro da linha) e uma aba `Resumo` com as contagens; sem `formato`, a resposta é JSON.

#### Scenario: Simular antes de aplicar
- **WHEN** a planilha com 30 prioridades trocadas é enviada com `modo=SIMULACAO&formato=xlsx`
- **THEN** a planilha volta com `ALTERACAO` nas 30 linhas e `SEM_MUDANCA` nas demais, e o cadastro não muda

#### Scenario: Um erro impede o lote inteiro
- **WHEN** uma planilha com 50 alterações tem uma linha com unidade inválida e é enviada com `modo=APLICACAO`
- **THEN** a resposta é 422 citando a aba, a linha e a coluna, e nenhuma das 50 alterações é gravada

#### Scenario: Curva alterada depois da exportação
- **WHEN** a curva `PRE` foi alterada pela API depois da exportação, e a planilha antiga é importada
- **THEN** a importação aplica o estado da planilha sobre o atual, e o `CADASTRO_ALTERADO` traz o estado que a `PRE` tinha antes
