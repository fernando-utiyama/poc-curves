## 1. Base comum

- [ ] 1.1 Configurar a autenticação do Entra ID com os papéis `Curvas.Leitura` e `Curvas.Cadastro`, o cliente HTTP do engine com token de serviço da identidade gerenciada (papel `Curvas.Leitura` no engine, tempo limite de 10 s), o tratamento de erros padronizado (tabela da spec `cadastro-curva-mercado`), o `X-Correlation-Id` e o relógio único em `America/Sao_Paulo`; verificar 401, 403, um teste por código de erro e o cabeçalho de correlação
- [ ] 1.2 Emitir o log `CADASTRO_ALTERADO` depois do commit de toda alteração (código, nome, tipo, operação, usuário, instante, `correlationId`, `idLote`, estado anterior e novo), sem nada no Blob; verificar os campos com um appender de teste, a ausência do evento quando o commit falha, e que o serviço não tem cliente de Blob
- [ ] 1.3 Criar o `ETag` da curva (SHA-256 do JSON canônico da curva, ligações e versões, sem `dBaseReft`, `cUsuarCalc` e campos de controle) e a exigência de `If-Match` em toda alteração; verificar 428 sem `If-Match`, 412 com `ETag` antigo, e que uma construção do engine (que grava `dBaseReft`) não muda o `ETag`

## 2. Curva de mercado

- [ ] 2.1 Criar as rotas de curva (listar com filtros e paginação, consultar com ligações e configuração vigente, criar, alterar, inativar, reativar), com as regras de campo, código único, nome único normalizado e imutável, e sem escrever `dBaseReft`, `cUsuarCalc` nem as colunas sem uso; verificar os cenários da spec (criação da DIxPRE, nome que colide, preço com cotação, tentativa de renomear, inativação)
- [ ] 2.2 Criar `GET .../{codigo}/auditoria` (JSON e `xlsx`, montado na hora com curva, ligações, versões e `ETag`); verificar os dois cenários da spec `cadastro-curva-mercado` e que o evento `CADASTRO_ALTERADO` traz o nome depois de uma troca de código

## 3. Ligações

- [ ] 3.1 Criar as rotas de ligação com as regras da spec (provedor existente, unicidade por curva e por prioridade, mesmo código em várias curvas) e o `idLigacao` por `MAX + 1` com `UPDLOCK, HOLDLOCK`; verificar os cenários da spec e duas inclusões simultâneas (SQL Server de teste, Testcontainers)
- [ ] 3.2 Criar `GET /ligacoes?provedor=&produto=&codigoNaFonte=` e os avisos `CURVA_SEM_ORIGEM` e `ORIGEM_INCOMPATIVEL_COM_MODELO`; verificar os dois avisos

- [ ] 3.3 Criar as regras da ligação com o provedor `TCEN` (mãe existente, não a própria, sem ciclo com o caminho na mensagem) e o aviso `CURVA_COM_FILHAS` na inativação; verificar os dois cenários da spec e um ciclo de três curvas

## 4. Configuração de cálculo

- [ ] 4.1 Implementar a validação de parâmetros com a tabela e as combinações da spec `curve-build-pipeline` do engine, e a gravação de `cModDado` em JSON compacto na ordem da tabela, até 1.024 caracteres; verificar um teste por regra, usando os mesmos casos da spec do engine
- [ ] 4.2 Implementar as versões com vigência contínua (primeira no passado, nova só a partir de hoje e depois da última, fechamento da anterior na mesma transação, exclusão só da última não iniciada) e as rotas (listar, vigente por data, validar sem gravar, criar, excluir); verificar os cenários da spec (troca a partir de amanhã, correção retroativa, desistência de versão futura, vigente numa data antiga)
- [ ] 4.3 Rejeitar alterações da curva que invalidem a versão vigente ou futura, e emitir o aviso `MODELO_NAO_NATIVO`; verificar a mudança de unidade que invalida a configuração

- [ ] 4.4 Criar `GET /api/v1/curvas-mercado/valores` (engine com token de serviço, 10 s, cache de 5 min, provedores de `tPrvdrDadoMercd`, cópia embutida com `VALORES_SEM_ENGINE`), os `enum` e descrições no OpenAPI, e o teste de contrato da tabela embutida contra o engine; verificar os dois cenários da spec e que o Swagger lista os valores de cada chave de `parametros`

## 5. Planilha

- [ ] 5.1 Criar a exportação (`.xlsx` com as abas `Curvas`, `Ligacoes`, `Configuracoes` e `Valores`, colunas exatas da spec, `Controle` com o `ETag`, listas suspensas restritivas e só com aviso conforme a spec); verificar a exportação de uma curva e de todas, e que a lista de `Interpolador` aceita um nome fora dela com aviso
- [ ] 5.2 Criar a importação com a semântica de estado desejado (inclusão, alteração e exclusão de ligações, versões novas, exclusão da última versão futura, erro em versão existente editada, erro em linha de curva ausente da aba `Curvas`) e a conferência do `Controle`; verificar que exportar e importar sem editar dá zero mudanças, e os cenários da spec
- [ ] 5.3 Criar os modos `SIMULACAO` (sem gravar) e `APLICACAO` (transação única, `idLote` nos eventos `CADASTRO_ALTERADO`, nada aplicado se houver erro), com resposta em JSON ou na planilha marcada (`Resultado` por linha e aba `Resumo`); verificar a simulação de 30 prioridades trocadas, um erro impedindo o lote inteiro e uma curva alterada depois da exportação

## 6. Painel

- [ ] 6.1 Criar `GET /api/v1/curvas-mercado/painel` com as colunas, a ordem das regras de situação, `atencao`, `atrasada` (horário esperado por provedor em configuração), contadores antes dos filtros, filtros e data-base padrão pelo calendário do engine; uma chamada a `GET /api/v1/curvas/situacao` (60 s) e à exportação de calendário do engine (10 s) por consulta, com token de serviço, com `SITUACAO_INDISPONIVEL` e `ENGINE_INDISPONIVEL` quando o engine não responde; verificar todos os cenários da spec `painel-curvas`, uma linha por situação e por `motivo` da tabela, e o painel com 7 curvas respondendo em menos de 3 segundos

## 7. Pontos

- [ ] 7.1 Criar `GET .../pontos?de=&ate=` e `GET .../pontos/{dataBase}` (dados gravados e `hashPontos`); verificar que o `hashPontos` é igual ao do engine para a mesma data, com o vetor de teste da spec `curve-build-pipeline` (valor na forma canônica, independente das 12 casas lidas do banco)
- [ ] 7.2 Criar `PUT .../pontos/{dataBase}`: valor como string decimal, arredondamento pela configuração vigente (sem ela, grava como enviado com `SEM_CONFIGURACAO`), transação com `UPDLOCK, ROWLOCK` na linha de `tCurvaMercd` esperando dentro do tempo limite da requisição, gravação só da diferença (`UPDATE`, `INSERT`, `DELETE`), releitura com conferência do `hashPontos` e desfazimento se divergir, `SEM_MUDANCA` sem escrita, aviso `VALOR_ARREDONDADO`, sem tocar `dBaseReft` e `cUsuarCalc`; verificar os cenários da spec (edição de um valor atualizando uma linha só, ponto retirado da lista, valor com casas a mais, lista igual à gravada sem escrita, conferência divergente simulada desfazendo tudo, curva digitada numa data sem construção, construção em andamento com SQL Server de teste)
- [ ] 7.3 Implementar as recusas de `PONTOS_INVALIDOS` só por consistência de banco (lista vazia, data ou valor malformado, data repetida, valor fora de `DECIMAL(28,12)`) e os avisos de regra de negócio da tabela da spec, com a checagem de feriados pela exportação de calendário do engine (token de serviço, 10 s); verificar uma recusa por regra de banco e, gravando com aviso, ponto em feriado, fim de semana, antes da data-base, preço não positivo, sem configuração e engine fora; taxa negativa sem aviso
- [ ] 7.4 Criar `DELETE .../pontos/{dataBase}`; verificar que a consulta do engine passa a responder `CURVA_NAO_CONSTRUIDA` e que uma construção posterior grava os pontos da fonte
- [ ] 7.5 Verificar a preferência: carga chegando depois da edição (engine devolve `EXISTENTE`, com `PONTOS_DIFERENTES_DA_FONTE` se diferir da fonte), recálculo depois da leitura (edição grava por cima), sem `If-Match`
- [ ] 7.6 Emitir o log `PONTOS_EDITADOS` (nível `AVISO`, usuário, operação, origem, `idLote`, quantidades e `hashPontos` antes e depois, horário de Brasília), sem auditoria; verificar os campos

## 8. Planilha de pontos

- [ ] 8.1 Criar a exportação (aba única `Pontos` com `Curva`, `DataBase`, `DataPonto` e `Valor`; valor numérico até 15 dígitos significativos e texto acima, limites de 50 códigos, 366 dias e 100.000 pontos); verificar a exportação de uma semana de duas curvas e o valor da `PTX` com 7 casas
- [ ] 8.2 Criar a importação com a semântica da spec (cada par curva/data-base como lista completa, pares ausentes intocados, sem exclusão de data inteira) e as mesmas recusas e avisos do `PUT`; verificar que exportar e importar sem editar dá zero mudanças, e a correção de um vértice em 5 datas
- [ ] 8.3 Criar os modos `SIMULACAO` e `APLICACAO` (transação única, travas em ordem alfabética de nome, 120 segundos, nada aplicado se houver recusa por consistência de banco, `idLote` no log), com gravação pela diferença com conferência do `hashPontos` por par, resposta em JSON ou planilha marcada (`Resultado`, `ValorGravado`, `Resumo`, `Exclusoes`); verificar os cenários da spec

## 9. Verificação

- [ ] 9.1 Cadastrar pela API e pela planilha as 7 curvas do primeiro objetivo, exatamente como em `exemplo-cadastro-7-curvas.txt`, com ligações e configurações conforme as specs do engine, e conferir que o engine as lê sem `CADASTRO_INVALIDO` (simulação do engine para cada uma)
- [ ] 9.2 Com o engine e o curves apontando para o mesmo SQL Server de teste: construir a `PRE` pelo engine, editar um ponto pelo curves, interpolar pelo engine com o valor novo, e confirmar que um webhook de carga não sobrescreve (e avisa `PONTOS_DIFERENTES_DA_FONTE`), que o painel mostra a `PRE` como `DIVERGENTE_DA_FONTE`, e que um recálculo forçado sobrescreve
- [ ] 9.3 Rodar `openspec validate curves-cadastro-curvas --strict` e a suíte de testes do serviço; verificar que tudo passa
