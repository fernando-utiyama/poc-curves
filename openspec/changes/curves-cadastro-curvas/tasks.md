## 1. Base comum

- [ ] 1.1 Configurar a autenticação do Entra ID com os papéis `Curvas.Leitura` e `Curvas.Cadastro`, o cliente HTTP do engine com token de serviço da identidade gerenciada (papel `Curvas.Leitura` no engine, tempo limite de 10 s), o tratamento de erros padronizado (tabela da spec `cadastro-curva-mercado`), o `X-Correlation-Id` e o relógio único em `America/Sao_Paulo`; verificar 401, 403, um teste por código de erro e o cabeçalho de correlação
- [ ] 1.2 Criar a auditoria no Blob (`auditoria-cadastro/{nome codificado}/`, `If-None-Match: *`, estado anterior e novo, `idLote`), com `AUDITORIA_PENDENTE` no log e nova tentativa em segundo plano quando o Blob falhar; verificar com Azurite a gravação, o Blob fora e a regravação
- [ ] 1.3 Criar o `ETag` da curva (SHA-256 do JSON canônico da curva, ligações e versões, sem `dBaseReft`, `cUsuarCalc` e campos de controle) e a exigência de `If-Match` em toda alteração; verificar 428 sem `If-Match`, 412 com `ETag` antigo, e que uma construção do engine (que grava `dBaseReft`) não muda o `ETag`

## 2. Curva de mercado

- [ ] 2.1 Criar as rotas de curva (listar com filtros e paginação, consultar com ligações e configuração vigente, criar, alterar, inativar, reativar), com as regras de campo, código único, nome único normalizado e imutável, e sem escrever `dBaseReft`, `cUsuarCalc` nem as colunas sem uso; verificar os cenários da spec (criação da DIxPRE, nome que colide, preço com cotação, tentativa de renomear, inativação)
- [ ] 2.2 Criar `GET .../{codigo}/historico`; verificar o cenário da unidade alterada e que o histórico continua completo depois de uma troca de código

## 3. Ligações

- [ ] 3.1 Criar as rotas de ligação com as regras da spec (provedor existente, unicidade por curva e por prioridade, mesmo código em várias curvas) e o `idLigacao` por `MAX + 1` com `UPDLOCK, HOLDLOCK`; verificar os cenários da spec e duas inclusões simultâneas (SQL Server de teste, Testcontainers)
- [ ] 3.2 Criar `GET /ligacoes?provedor=&produto=&codigoNaFonte=` e os avisos `CURVA_SEM_ORIGEM` e `ORIGEM_INCOMPATIVEL_COM_MODELO`; verificar os dois avisos

## 4. Configuração de cálculo

- [ ] 4.1 Implementar a validação de parâmetros com a tabela e as combinações da spec `curve-build-pipeline` do engine, e a gravação de `cModDado` em JSON compacto na ordem da tabela, até 1.024 caracteres; verificar um teste por regra, usando os mesmos casos da spec do engine
- [ ] 4.2 Implementar as versões com vigência contínua (primeira no passado, nova só a partir de hoje e depois da última, fechamento da anterior na mesma transação, exclusão só da última não iniciada) e as rotas (listar, vigente por data, validar sem gravar, criar, excluir); verificar os cenários da spec (troca a partir de amanhã, correção retroativa, desistência de versão futura, vigente numa data antiga)
- [ ] 4.3 Rejeitar alterações da curva que invalidem a versão vigente ou futura, e emitir o aviso `MODELO_NAO_NATIVO`; verificar a mudança de unidade que invalida a configuração

- [ ] 4.4 Criar `GET /api/v1/curvas-mercado/valores` (engine com token de serviço, 10 s, cache de 5 min, provedores de `tPrvdrDadoMercd`, cópia embutida com `VALORES_SEM_ENGINE`), os `enum` e descrições no OpenAPI, e o teste de contrato da tabela embutida contra o engine; verificar os dois cenários da spec e que o Swagger lista os valores de cada chave de `parametros`

## 5. Planilha

- [ ] 5.1 Criar a exportação (`.xlsx` com as abas `Curvas`, `Ligacoes`, `Configuracoes` e `Valores`, colunas exatas da spec, `Controle` com o `ETag`, listas suspensas restritivas e só com aviso conforme a spec); verificar a exportação de uma curva e de todas, e que a lista de `Interpolador` aceita um nome fora dela com aviso
- [ ] 5.2 Criar a importação com a semântica de estado desejado (inclusão, alteração e exclusão de ligações, versões novas, exclusão da última versão futura, erro em versão existente editada, erro em linha de curva ausente da aba `Curvas`) e a conferência do `Controle`; verificar que exportar e importar sem editar dá zero mudanças, e os cenários da spec
- [ ] 5.3 Criar os modos `SIMULACAO` (sem gravar) e `APLICACAO` (transação única, `idLote` na auditoria, nada aplicado se houver erro), com resposta em JSON ou na planilha marcada (`Resultado` por linha e aba `Resumo`); verificar a simulação de 30 prioridades trocadas, um erro impedindo o lote inteiro e uma curva alterada depois da exportação

## 6. Painel

- [ ] 6.1 Criar `GET /api/v1/curvas-mercado/painel` com as colunas, a ordem das regras de situação, `atencao`, `atrasada` (horário esperado por provedor em configuração), contadores antes dos filtros, filtros e data-base padrão pelo calendário do engine; uma chamada a `GET /api/v1/curvas/situacao` e à exportação de calendário do engine por consulta (token de serviço, 10 s), com `SITUACAO_INDISPONIVEL` e `ENGINE_INDISPONIVEL` quando o engine não responde; verificar todos os cenários da spec `painel-curvas`, uma linha por situação da tabela, e o painel com 7 curvas respondendo em menos de 2 segundos

## 7. Verificação

- [ ] 7.1 Cadastrar pela API e pela planilha as 7 curvas do primeiro objetivo, exatamente como em `exemplo-cadastro-7-curvas.txt`, com ligações e configurações conforme as specs do engine, e conferir que o engine as lê sem `CADASTRO_INVALIDO` (simulação do engine para cada uma)
- [ ] 7.2 Rodar `openspec validate curves-cadastro-curvas --strict` e a suíte de testes do serviço; verificar que tudo passa
