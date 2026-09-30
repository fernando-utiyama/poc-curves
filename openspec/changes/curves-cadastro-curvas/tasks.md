Guia de implementação passo a passo (entidades, regras, SQL, `ETag`, planilhas, painel, pontos e vetores de teste reais): [`implementacao.md`](implementacao.md). Siga a ordem da seção 13 do guia. Os "verificar" de cada tarefa viram testes escritos ao final (seção 14 do guia), só com mocks; o que depende de banco ou do engine real é conferido na homologação (seção 15).

**Escopo:** esta change é a primeira parte do curves: CRUD da curva de mercado, das ligações com provedores, da configuração de cálculo (com os valores aceitos) e do bruto da B3. Planilha do cadastro, painel, pontos, planilha de pontos e origens secundárias estão na change `curves-operacao-curvas`, que vem depois; onde um requisito daqui cita algo de lá (planilha, `idLote`, `MODELOS_POR_ORIGEM`, origem secundária), isso passa a valer com ela. As tarefas mantêm a numeração original, para as referências do guia valerem.

## 1. Base comum

- [ ] 1.1 Configurar a autenticação do Entra ID com os papéis `Curvas.Leitura` e `Curvas.Cadastro`, o cliente HTTP do engine com token de serviço da identidade gerenciada (papéis `Curvas.Leitura` e `Curvas.Operador` no engine, tempo limite de 10 s, 60 s na regravação da interpolada), o tratamento de erros padronizado (tabela da spec `cadastro-curva-mercado`), o `X-Correlation-Id` e o fuso da JVM em `America/Sao_Paulo` (sem classe de relógio); verificar 401, 403, um teste por código de erro e o cabeçalho de correlação
- [ ] 1.2 Emitir o log `CADASTRO_ALTERADO` depois do commit de toda alteração (código, nome, tipo, operação, usuário, instante, `correlationId`, `idLote`, estado anterior e novo), sem nada no Blob; verificar os campos com um appender de teste, a ausência do evento quando o commit falha, e que o serviço não tem cliente de Blob
- [ ] 1.2b Aplicar o contrato de tipos (decimais como string, datas, instantes, enums com caixa exata, `avisos` e `detalhes` no formato comum ao engine), aparar espaços das colunas `CHAR` lidas e incluir os enums e catálogos do serviço em `GET /curvas-mercado/valores`; verificar os dois cenários da spec `cadastro-curva-mercado`, mensagens, rótulos e descrições em pt-BR com acentuação, um teste de contrato por enum e por código de aviso contra as tabelas da spec, e o `ETag` igual antes e depois de reler uma curva
- [ ] 1.3 Criar o `ETag` da curva (SHA-256 do JSON canônico da curva, ligações e versões, sem `dBaseReft`, `cUsuarCalc` e campos de controle) e a exigência de `If-Match` em toda alteração; verificar 428 sem `If-Match`, 412 com `ETag` antigo, e que uma construção do engine (que grava `dBaseReft`) não muda o `ETag`

## 2. Curva de mercado

- [ ] 2.1 Criar as rotas de curva (listar com filtros e paginação, consultar com ligações e configuração vigente, criar, alterar, inativar, reativar), com as regras de campo, código único, nome único normalizado e imutável, e sem escrever `dBaseReft`, `cUsuarCalc` nem as colunas sem uso; verificar os cenários da spec (criação da DIxPRE, nome que colide, preço com cotação, tentativa de renomear, inativação)
- [ ] 2.2 Criar `GET .../{codigo}/auditoria` (JSON e `xlsx`, montado na hora com curva, ligações, versões e `ETag`); verificar os dois cenários da spec `cadastro-curva-mercado` e que o evento `CADASTRO_ALTERADO` traz o nome depois de uma troca de código

## 3. Ligações

- [ ] 3.1 Criar as rotas de ligação com as regras da spec (provedor existente, unicidade por curva e por prioridade, mesmo código em várias curvas) e o `idLigacao` por `MAX + 1` com `UPDLOCK, HOLDLOCK`; verificar os cenários da spec com o acesso ao banco simulado; duas inclusões simultâneas são conferidas na homologação
- [ ] 3.2 Criar `GET /ligacoes?provedor=&produto=&codigoNaFonte=` e os avisos `CURVA_SEM_ORIGEM` e `ORIGEM_INCOMPATIVEL_COM_MODELO`; verificar os dois avisos

- [ ] 3.3 Criar as regras da ligação com o provedor `TCEN` (componente existente, não a própria, sem ciclo com o caminho na mensagem) e o aviso `CURVA_COM_FILHAS` na inativação; verificar os dois cenários da spec e um ciclo de três curvas

## 4. Configuração de cálculo

- [ ] 4.1 Implementar a validação de parâmetros com a tabela e as combinações da spec `curve-build-pipeline` do engine, e a gravação de `cModDado` em JSON compacto na ordem da tabela, até 1.024 caracteres; verificar um teste por regra, usando os mesmos casos da spec do engine
- [ ] 4.2 Implementar as versões com vigência contínua (primeira no passado, nova só a partir de hoje e depois da última, fechamento da anterior na mesma transação, exclusão só da última não iniciada) e as rotas (listar, vigente por data, validar sem gravar, criar, excluir); verificar os cenários da spec (troca a partir de amanhã, correção retroativa, desistência de versão futura, vigente numa data antiga)
- [ ] 4.3 Rejeitar alterações da curva que invalidem a versão vigente ou futura, e emitir o aviso `MODELO_NAO_NATIVO`; verificar a mudança de unidade que invalida a configuração

- [ ] 4.4 Criar `GET /api/v1/curvas-mercado/valores` (engine com token de serviço, 10 s, cache de 5 min, provedores de `tPrvdrDadoMercd`, cópia embutida com `VALORES_SEM_ENGINE`), os `enum` e descrições no OpenAPI, e o teste de contrato da tabela embutida contra o engine; verificar os dois cenários da spec e que o Swagger lista os valores de cada chave de `parametros`

## 5. Curva primária B3

- [ ] 5.1 Criar `GET /curvas-mercado/primaria-b3` (uma consulta agregada por curva e data-base em `tBtrsCurvaPrimr`, com `quantidadeLinhas`, `codigosNaFonte` das ligações `B3`/`TS`, `curvaConstruida`, filtros `de`/`ate` com padrão de 30 dias e máximo de 366, `codigo`, `nome`, paginação 50/500) e `GET .../{codigo}/primaria-b3/{dataBase}` (linhas ordenadas por dias corridos e id, `dataPonto`, avisos da data); verificar os cenários "Seleção depois da carga" e "Consulta das linhas da PRE", e que a listagem é uma consulta só
- [ ] 5.2 Criar `POST .../linhas`, `PUT .../linhas/{id}`, `DELETE .../linhas/{id}` e `DELETE .../primaria-b3/{dataBase}`: transação com a trava da curva (`UPDLOCK, ROWLOCK`, 60 s só para obtê-la), `cldtfdUnic` por `MAX + 1` com `UPDLOCK, HOLDLOCK`, valores sem arredondamento, 404 para linha de outra curva ou data, recusa 422 `DADOS_INVALIDOS` só pelas regras de coluna, avisos `DIAS_CORRIDOS_NAO_POSITIVO`, `DIAS_UTEIS_INCOERENTES`, `DIAS_CORRIDOS_REPETIDOS`, `CURVA_SEM_LIGACAO_B3` e `CURVA_JA_CONSTRUIDA` sobre todas as linhas da data, nenhuma chamada ao engine e nenhuma escrita fora de `tBtrsCurvaPrimr`; verificar os cenários da spec `curva-primaria-b3`
- [ ] 5.3 Emitir o log `CURVA_PRIMARIA_EDITADA` (nível `AVISO`, operação, linha antes e depois, quantidades, horário de Brasília) depois do commit, sem auditoria; verificar os campos e a ausência do evento quando o commit falha

## 11. Verificação

- [ ] 11.1 Cadastrar pela API as 7 curvas do primeiro objetivo, exatamente como em `exemplo-cadastro-7-curvas.txt`, com ligações e configurações conforme as specs do engine, e conferir que o engine as lê sem `CADASTRO_INVALIDO` (a planilha do cadastro vem na change `curves-operacao-curvas`)
- [ ] 11.3 Rodar `openspec validate curves-cadastro-curvas --strict` e a suíte de testes do serviço; verificar que tudo passa
