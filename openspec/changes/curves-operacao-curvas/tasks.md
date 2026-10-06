Segunda parte do curves (planilha do cadastro, painel, vértices, planilha de vértices e origens secundárias). A primeira parte, os CRUDs, está na change [`curves-cadastro-curvas`](../curves-cadastro-curvas/tasks.md) e vem antes; o guia de implementação e o design são os de lá: [`implementacao.md`](../curves-cadastro-curvas/implementacao.md). As tarefas mantêm a numeração original.

> **Esta change é de conferência: boa parte do curves já foi desenvolvida no repositório real**, muitas vezes com outro nome (por exemplo, `Ponto` ou `Linha` no lugar de `Vertice`, ou em inglês). A primeira parte (`curves-cadastro-curvas`) já foi aplicada no repositório real numa versão anterior, que ainda incluía parte do que hoje está aqui (ex.: cliente do engine, vértices manuais); por isso algo desta change pode já existir. Cada tarefa SHALL seguir este roteiro:
> 1. **Conferir:** procurar pela **funcionalidade** (o que faz, que tabela lê ou grava, que rota expõe), nunca só pelo nome da spec.
> 2. Decidir uma de três saídas:
>    - **já existe e cumpre a spec → deixar como está** (não reescrever, não "melhorar"; só o nome segue o passo 3);
>    - **existe e diverge → alterar** só o que diverge, no código que já existe;
>    - **não existe → criar**, com os nomes desta change.
> 3. Nunca criar uma segunda versão do que já está pronto. **Nome diferente do desta change é divergência:** renomeie para o nome da change (classe, arquivo, método, campo interno e todas as referências, até compilar), menos o que outro serviço ou o banco já usa (rota exposta, campo de JSON de resposta, coluna, tópico): isso fica como está e vai para a anotação como `nome-real → nome-da-change`.
> 4. Ao marcar a tarefa, anotar ao lado a saída e o arquivo: `[conferido: pronto | alterado | renomeado de X | criado] caminho/Arquivo.java`. Na dúvida se é a mesma coisa, `// TODO(revisao): <dúvida>` e siga.
> 5. Código que a spec atual não pede (sobra da versão anterior): não apagar; anotar como `[sobra] caminho/Arquivo.java` no resumo, para a revisão decidir.

## 1. Cliente do engine (chegou da primeira parte)

> A v1.1 já tem a `EnginePort` e o `EngineHttpClient` de repasse (change `curves-v1-1`): acrescente os métodos desta fase nessa mesma porta e nesse mesmo cliente, sem outra porta nem outro cliente HTTP.

- [ ] 1.1 Configurar o cliente HTTP do engine, sem autenticação (o engine não exige token), com tempo limite de 10 s nas consultas leves (calendário, `valores-cadastro`) e 60 s em `situacao` e na regravação da interpolada, `X-Correlation-Id` e falha (rede, tempo, 4xx, 5xx, JSON ilegível) como resultado "engine indisponível", nunca exceção (guia, seção 1.6); acrescentar ao `GET /api/v1/curvas-mercado/valores` a consulta ao engine (`GET /api/v1/valores-cadastro`, cache de 5 minutos), que traz os scripts Groovy ativos, devolvendo a tabela local com o aviso `VALORES_SEM_ENGINE` quando o engine não responde, e o teste de contrato que compara a tabela local com a parte fixa da resposta do engine; verificar o cliente com resposta simulada em cada falha, os dois cenários "Scripts Groovy no formulário" e "Engine fora" da spec `configuracao-calculo-curva`, e que qualquer diferença entre a tabela local e a do engine falha o build

## 6. Planilha

- [ ] 6.1 Criar a exportação (`.xlsx` com as abas `Curvas`, `Provedores`, `Configuracoes` e `Valores`, colunas exatas da spec, listas suspensas restritivas e só com aviso conforme a spec); verificar a exportação de uma curva e de todas, e que a lista de `Interpolador` aceita um nome fora dela com aviso
- [ ] 6.2 Criar a importação (datas e números em texto no formato pt-BR aceitos, como na spec) com a semântica de estado desejado (inclusão, alteração e exclusão de provedores da curva, versões novas, exclusão da última versão futura, erro em versão existente editada, erro em linha de curva ausente da aba `Curvas`); verificar que exportar e importar sem editar dá zero mudanças, e os cenários da spec
- [ ] 6.3 Criar os modos `SIMULACAO` (sem gravar) e `APLICACAO` (transação única, `idLote` nos eventos `CADASTRO_ALTERADO`, nada aplicado se houver erro), com resposta em JSON ou na planilha marcada (`Resultado` por linha e aba `Resumo`); verificar a simulação de 30 prioridades trocadas, um erro impedindo o lote inteiro e uma curva alterada depois da exportação

## 7. Painel

- [ ] 7.1 Criar `GET /api/v1/curvas-mercado/painel` com as colunas, a ordem das regras de situação, `atencao`, `atrasada` (só para data-base passada), contadores antes dos filtros, filtros e data-base padrão pelo calendário do engine; uma chamada a `GET /api/v1/curvas/situacao` (60 s) e à exportação de calendário do engine (10 s) por consulta, pelo cliente da tarefa 1.1, com `SITUACAO_INDISPONIVEL` e `ENGINE_INDISPONIVEL` quando o engine não responde; verificar todos os cenários da spec `painel-curvas` (inclusive `NAO_CONSTRUIDA` e `AGUARDANDO_CARGA`), uma linha por situação e por `motivo` da tabela, e o painel com 7 curvas respondendo em menos de 3 segundos

## 8. Vértices

- [ ] 8.1 Criar `GET .../vertices?de=&ate=` e `GET .../vertices/{dataBase}` (dados gravados e `hashPontos`); verificar que o `hashPontos` é igual ao do engine para a mesma data, com o vetor de teste da spec `curve-build-pipeline` (valor na forma canônica, independente das 12 casas lidas do banco)
- [ ] 8.2 Criar `PUT .../vertices/{dataBase}`: valor como string decimal, arredondamento pela configuração vigente (sem ela, grava como enviado com `SEM_CONFIGURACAO`), transação com `UPDLOCK, ROWLOCK` na linha de `tCurvaMercd` esperando até 60 s por `@QueryHints` `jakarta.persistence.query.timeout=60000` (estouro → 500 `ERRO_INTERNO`, sem gravar), gravação em `tDadoVertcCurva` só dos vértices novos, alterados ou excluídos (tarefa 10.4), chamada à regravação da interpolada no engine depois do commit (também com `SEM_MUDANCA`), com o aviso `INTERPOLADA_DESATUALIZADA` quando ela falhar, gravação só da diferença (`UPDATE`, `INSERT`, `DELETE`), releitura com conferência do `hashPontos` e desfazimento se divergir, `SEM_MUDANCA` sem escrita, aviso `VALOR_ARREDONDADO`, sem tocar `dBaseReft` e `cUsuarCalc`; verificar os cenários da spec (edição de um valor atualizando uma linha só, vértice retirado da lista, valor com casas a mais, lista igual à gravada sem escrita, conferência divergente simulada desfazendo tudo, curva digitada numa data sem construção)
- [ ] 8.3 Implementar as recusas de `VERTICES_INVALIDOS` só por consistência de banco (lista vazia, data ou valor malformado, data repetida, valor fora de `DECIMAL(28,12)`) e os avisos de regra de negócio da tabela da spec, com a checagem de feriados pela exportação de calendário do engine (cliente da tarefa 1.1, 10 s); verificar uma recusa por regra de banco e, gravando com aviso, vértice em feriado, fim de semana, antes da data-base, preço não positivo, sem configuração e engine fora; taxa negativa sem aviso
- [ ] 8.4 Criar `DELETE .../vertices/{dataBase}` (apagando os vértices em `tDadoVertcCurva` e a interpolada da data em `tDadoCurva`); verificar que a consulta do engine passa a responder `CURVA_NAO_CONSTRUIDA` e que uma construção posterior grava os vértices da fonte
- [ ] 8.5 Verificar a preferência: carga chegando depois da edição (engine devolve `EXISTENTE`, com `PONTOS_DIFERENTES_DA_FONTE` se diferir da fonte), recálculo depois da leitura (edição grava por cima), sem conferência de versão
- [ ] 8.6 Emitir o log `VERTICES_EDITADOS` (nível `AVISO`, usuário, operação, origem, `idLote`, quantidades e `hashPontos` antes e depois, horário de Brasília), sem auditoria (com a autenticação adiada, `usuario` sai nulo); verificar os campos

## 9. Planilha de vértices

- [ ] 9.1 Criar a exportação (aba única `Vertices` com `Curva`, `DataBase`, `DataVertice`, `Valor` e `DiasUteis`; valor numérico até 15 dígitos significativos e texto acima, limites de 50 códigos, 366 dias e 100.000 vértices); verificar a exportação de uma semana de duas curvas e o valor da `PTX` com 7 casas
- [ ] 9.2 Criar a importação com a semântica da spec, aceitando datas em texto `dd/mm/aaaa` ou `aaaa-mm-dd` e números com vírgula ou ponto decimal (recusando os dois juntos), (cada par curva/data-base como lista completa, pares ausentes intocados, sem exclusão de data inteira) e as mesmas recusas e avisos do `PUT`; verificar que exportar e importar sem editar dá zero mudanças, e a correção de um vértice em 5 datas
- [ ] 9.3 Criar os modos `SIMULACAO` e `APLICACAO` (transação única, travas em ordem alfabética de nome, 120 segundos, nada aplicado se houver recusa por consistência de banco, `idLote` no log), com gravação pela diferença com conferência do `hashPontos` por par, resposta em JSON ou planilha marcada (`Resultado`, `ValorGravado`, `Resumo`, `Exclusoes`); verificar os cenários da spec

## 9b. Planilha dos vértices brutos

- [ ] 9b.1 Criar a exportação e a importação (`SIMULACAO`/`APLICACAO`, lista completa por `id`, `formato=xlsx`) dos vértices brutos sobre as rotas `/dados-mercado` da primeira parte (guia da change `curves-cadastro-curvas`, §11.4), com `CURVA_PRIMARIA_EDITADA` operação `PLANILHA`; verificar os cenários da spec `vertices-brutos-planilha`
- [ ] 9b.2 Acrescentar `formato=xlsx` à auditoria do cadastro (`GET /curvas-mercado/{codigo}/auditoria`, abas e nome de arquivo do guia da primeira parte); verificar os dois formatos

## 10. Origens secundárias

- [ ] 10.1 Aceitar `MODELOS_POR_ORIGEM` em `parametros` (formato da chave, valor de 1 a 100 caracteres) com os avisos `MODELO_POR_ORIGEM_SEM_PROVEDOR`, `ORIGEM_INCOMPATIVEL_COM_MODELO` e `MODELO_NAO_NATIVO`, e o aviso na exclusão de provedor da curva; verificar os dois cenários da spec `configuracao-calculo-curva` e a entrada da tabela embutida no teste de contrato com o engine
- [ ] 10.2 Ler e escrever `MODELOS_POR_ORIGEM` na planilha (`{provedor}/{produto}={modelo}` separados por `;`, em ordem alfabética na exportação); verificar o cenário da spec `cadastro-curvas-planilha`, célula malformada e exportar-importar sem alteração
- [ ] 10.3 Acrescentar `origensSecundarias` às linhas do painel; verificar o cenário da data construída pela origem secundária

- [ ] 10.4 Aceitar `diasUteis` opcional no `PUT` de vértices e a coluna `DiasUteis` na planilha de vértices (exportada com `tDadoVertcCurva.cDiaUtil`), regravando em `tDadoVertcCurva` só a linha dos vértices novos ou alterados (dias informados, dias corridos, 30/360, fatores nulos) e mantendo a do engine nos demais, com os avisos `DIAS_UTEIS_DIFERENTES_DO_CALENDARIO`, `DIAS_UTEIS_INCOERENTES` e `DIAS_UTEIS_FORA_DE_ORDEM`; verificar os cenários das specs `vertices-curva-manual` e `vertices-curva-planilha`, planilha antiga sem a coluna e exportar-importar sem mudança

## 11. Verificação

- [ ] 11.1 Cadastrar pela planilha as 7 curvas de `exemplo-cadastro-7-curvas.txt` (change `curves-cadastro-curvas`) e conferir que exportar e importar sem editar dá zero mudanças
- [ ] 11.3 Rodar `openspec validate curves-operacao-curvas --strict` e a suíte de testes do serviço; verificar que tudo passa

## 12. Pendências da v1 (inspeção de 2026-10-06)

> Itens que a inspeção da curves real (`docs/inspecao/inspecao-curves.md` do poc) apontou e que ficaram fora da `curves-v1-1`. A regra de cada um já está nas specs da change `curves-cadastro-curvas` (citada entre parênteses). Cada tarefa é de conferência: procure pela funcionalidade, altere só o que diverge e anote a saída.

- [ ] 12.1 Rotas `/dados-mercado/{provedor}` unificadas (tickers e consulta por código na fonte e data, com avisos) sobre os CRUDs `primaria-*` que já existem (spec `vertices-brutos-provedor`)
- [ ] 12.2 Repasse de `GET /curvas-mercado/{codigo}/{dataBase}/pontos?de=&ate=` ao engine (depois da rota de pontos do engine, `engine-modelos-curva` 18.3) e do `X-Usuario` recebido (spec `acoes-curva-mercado`)
- [ ] 12.3 Formato de erro com `correlationId` no lugar de `errorId` e `detalhes` sempre presente (spec `cadastro-curva-mercado`, "Sem autenticação e erros")
- [ ] 12.4 Decimais da ANBIMA e da Bloomberg como texto, como a B3 (spec `cadastro-curva-mercado`, "Contrato de tipos para o front")
- [ ] 12.5 Instantes com o deslocamento de Brasília (`-03:00`), não UTC (spec `cadastro-curva-mercado`, "Horário e log")
- [ ] 12.6 `avisos` sempre presente, inclusive nos provedores da curva e nos brutos (spec `cadastro-curva-mercado`, "Contrato de tipos para o front")
- [ ] 12.7 `PUT` do provedor da curva que troca o provedor → 422 `DADOS_INVALIDOS`; aviso `CURVA_COM_FILHAS` na inativação (spec `provedor-curva`)
- [ ] 12.8 Configuração: decidir a correção retroativa (início de vigência no passado) e alinhar código e spec (spec `configuracao-calculo-curva`, "Vigência sem sobreposição e sem buraco")
- [ ] 12.9 `/valores`: rótulo e descrição dos valores dos parâmetros de construção e os `enum` no Swagger (spec `configuracao-calculo-curva`, "Valores aceitos para o front e o Swagger")
