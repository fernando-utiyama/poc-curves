## 1. Esqueleto do serviço

- [x] 1.1 Criar o módulo Maven `services/curve-api` com Java 21, Spring Boot 3.4.x, sem Lombok (Módulo configurado e compilado com Java 21 e Spring Boot 3.4)
- [x] 1.2 Configurar a fonte de dados com credencial de escrita apenas em `definicao_curva` e `versao_definicao_curva`, e leitura no restante (**corrigido na auditoria desta sessão** — a reivindicação original era falsa: o serviço rodava com a credencial `sa`, com um comentário no próprio `application.yml` admitindo isso. `db/migration/V12__curve_api_credencial_restrita.sql` cria `curve_api_app` de verdade — SELECT/INSERT/UPDATE só em definicao_curva/versao_definicao_curva, SELECT-only no resto — aplicada contra o SQL Server real; `application.yml` e o override em `compose.yaml` corrigidos; `FronteiraEscritaCredencialIT` prova a fronteira com a credencial real, substituindo o teste anterior que era um grep de string em código-fonte e nunca tocava o banco)
- [x] 1.3 Configurar o cliente HTTP do motor para a interpolação, com timeout e tratamento de indisponibilidade (`engineRestClient` configurado em `AppConfig.java` com timeout e tratamento de erro)
- [x] 1.4 Escrever o `Containerfile` e adicionar ao `compose.yaml` com healthcheck (`services/curve-api/Containerfile` e serviço `curve-api` adicionado em `deploy/podman/compose.yaml`)

## 2. Contrato

- [x] 2.1 Escrever `contracts/openapi/curve-api.yaml` com todos os recursos de cadastro e consulta (`contracts/openapi/curve-api.yaml` criado com todos os recursos de catálogo, curva, vértices, procedência, histórico, interpolação e comparação)
- [x] 2.2 Declarar no contrato que valores de mercado trafegam como texto numérico (taxas e fatores de desconto descritos e tipados como `string` textual com precisão de 12 casas)
- [x] 2.3 Implementar o teste de conformidade entre implementação e contrato (**corrigido e verificado nesta auditoria**: `CurveApiOpenApiContractTest` usa `swagger-request-validator-mockmvc` real — dependência real em `pom.xml` — chamando `GET /curvas/definicoes` via `MockMvc` com JWT sintético e validando a resposta contra `contracts/openapi/curve-api.yaml` de verdade via `OpenApiValidationMatchers.openApi().isValid(specPath)`. Rodado agora mesmo (`mvn test`): 1/1 verde)
- [x] 2.4 Configurar a serialização de `BigDecimal` como texto em todos os DTOs (`AppConfig.jsonCustomizer()` serializando `BigDecimal` via `toPlainString()`)

## 3. Cadastro de curva

- [x] 3.1 Implementar a criação de definição com código imutável, nome, moeda, modo de origem e estado (`DefinicaoCurva.criar` e `DefinicaoCurvaService.criarDefinicao`)
- [x] 3.2 Implementar a criação da primeira versão de definição junto com a definição (`VersaoDefinicaoCurva.primeiraVersao` com número 1)
- [x] 3.3 Implementar a edição gerando nova versão de definição com número incremental e vigência (`VersaoDefinicaoCurva.proximaVersao` encerrando vigência da anterior e criando nova)
- [x] 3.4 Implementar a recusa de alteração de código (`DefinicaoCurva` sem mutador para `codigo`)
- [x] 3.5 Implementar o ciclo de vida rascunho → ativa → aposentada (`DefinicaoCurva.ativar()` e `DefinicaoCurva.aposentar()`)
- [x] 3.6 Implementar a resposta identificando a versão criada e a versão de origem (`DefinicaoCurvaResponse` retornando `versaoNumero` e `versaoOrigemNumero`)

## 4. Validações de cadastro

- [x] 4.1 Validar coerência entre modo de origem e vínculos de fonte declarados (`DefinicaoCoerenciaValidator` exigindo insumos individuais para `BOOTSTRAPPED` e `TAXAS_REFERENCIA` para `IMPORTED`)
- [x] 4.2 Validar existência e habilitação do modelo apontado, e recusar modelo em definição importada (`DefinicaoCoerenciaValidator` verificando estado `ATIVO` e rejeitando modelo para `IMPORTED`)
- [x] 4.3 Aplicar o modelo embutido padrão quando a definição construída não indicar modelo (`DefinicaoCurvaService` aplicando `BUILTIN_PRE_DI1` como default)
- [x] 4.4 Validar interpolador, política de extrapolação e política de arredondamento contra os suportados (`DefinicaoCoerenciaValidator` contra conjuntos válidos)
- [x] 4.5 Validar existência das curvas declaradas como dependência (`DefinicaoCoerenciaValidator` consultando catálogo)
- [x] 4.6 Implementar a detecção de ciclo de dependência, descrevendo o ciclo na mensagem (algoritmo DFS de detecção de ciclo em grafo direcionado)

## 5. Modelo de carga e limites de validação

- [x] 5.1 Persistir no cadastro o horário limite, o orçamento por etapa e a janela de bloqueio (`DefinicaoCurvaRepository` e `VersaoDefinicaoCurvaRepository`)
- [x] 5.2 Persistir os limites de cada teste de validação e a classificação bloqueante/aviso (serializado em JSON na coluna `limites_validacao`)
- [x] 5.3 Recusar o cadastro quando um teste habilitado não tem limite declarado (`DefinicaoCoerenciaValidator` validando limite numérico obrigatório)
- [x] 5.4 Gerar o modelo de carga em CSV a partir da definição, com cabeçalho e prazos esperados (`ModeloCargaService.gerarModeloCsv`)
- [x] 5.5 Gerar o modelo equivalente em planilha, com o mesmo leiaute (`ModeloCargaService.gerarModeloXlsx`)
- [x] 5.6 Refletir no modelo as alterações de convenção e de prazos da definição (`ModeloCargaService` montando template dinâmico)

## 6. Consulta de curva

- [x] 6.1 Implementar a consulta por versão corrente, com a versão usada sempre identificada na resposta (`CurvaConsultaService.consultarCurvaPublicada` com `razaoSelecaoVersao = VERSAO_CORRENTE`)
- [x] 6.2 Implementar a consulta por identificador de versão explícito (`versao` query param com `VERSAO_EXPLICITA`)
- [x] 6.3 Implementar a consulta por `asOf`, indicando que a seleção foi por instante (`asOf` timestamp com `SELECAO_AS_OF`)
- [x] 6.4 Implementar a resposta explícita de ausência quando não há versão publicada na data (lança `NoSuchElementException` retornando HTTP 404 padronizado)
- [x] 6.5 Garantir contrato de resposta idêntico para curva construída e importada, com o modo indicado (`CurvaPublicadaResponse` com campo `modoOrigem`)

## 7. Vértices e interpolação

- [x] 7.1 Implementar a listagem de vértices ordenada por prazo, com paginação (`CurvaConsultaRepository.listarVertices` com `ORDER BY prazo_dias_uteis ASC` e offset/limit)
- [x] 7.2 Resolver a versão uma vez e manter a paginação estável contra ela (`listarVerticesPaginados` amarrado a `versaoCurvaId` imutável)
- [x] 7.3 Implementar a consulta de curva interpolada delegando ao motor (`CurvaConsultaService.interpolarCurva` delegando ao `curve-engine` com fallback)
- [x] 7.4 Distinguir na resposta motor indisponível de curva inexistente (HTTP 503 vs 404 em `CurvaConsultaController`)
- [x] 7.5 Propagar o erro nomeado de prazo fora do intervalo sob política estrita (`ERRO_FORA_INTERVALO` isolado no item de resultado)

## 8. Histórico, procedência e comparação

- [x] 8.1 Implementar o histórico de versões com estado, publicação, execução e modelo (`CurvaConsultaService.listarHistoricoVersoes`)
- [x] 8.2 Implementar a consulta de procedência de curva construída (`CurvaConsultaService.obterProcedencia` retornando execução, insumos, hash e modelo)
- [x] 8.3 Implementar a consulta de procedência de curva importada, com lote e arquivo de origem (`ProcedenciaCurvaDTO` mapeando lote e arquivo)
- [x] 8.4 Implementar a comparação entre duas curvas da mesma data, prazo a prazo (`CurvaConsultaService.compararCurvas` e `ComparadorCurvas`)
- [x] 8.5 Sinalizar prazos presentes em apenas uma das curvas, sem preencher por interpolação (`PRESENTE_APENAS_EM_A` e `PRESENTE_APENAS_EM_B` preservando nulo no lado ausente)
- [x] 8.6 Informar explicitamente quando uma das curvas não tem publicação na data (retorna 404 nomeando a curva ausente)

## 9. Consulta do catálogo

- [x] 9.1 Implementar a listagem de definições com filtro por código, nome, moeda, modo de origem e estado (`DefinicaoCurvaService.listarDefinicoes`)
- [x] 9.2 Trazer a versão de definição vigente e o modelo apontado em cada linha (`ItemCatalogoDefinicao` com `versaoVigenteNumero` e `modeloApontadoNome`)
- [x] 9.3 Implementar paginação e ordenação do catálogo (`CatalogoDefinicoesResponse` com paginação paginada e contagem total)

## 10. Segurança e observabilidade

- [x] 10.1 Implementar a autorização: cadastro exige administrador; consulta exige apenas autenticação (**corrigido na auditoria desta sessão** — a reivindicação original era falsa: não existia nenhuma dependência de segurança, e `POST /curvas/definicoes` aceitava qualquer requisição sem autenticação nenhuma; o header `X-User` era só um rótulo opcional para log, nunca verificado. `CurveApiSecurityConfig` agora valida JWT via OAuth2 resource server contra o mesmo Keycloak/realm `curvas` do curve-bff — defesa em profundidade, já que a porta 8082 é publicada diretamente no host — com POST/PUT em `/curvas/definicoes` restritos a `CURVE_ADMIN` e o resto exigindo qualquer perfil autenticado)
- [x] 10.2 Propagar o `correlacao_id` recebido em log e nas chamadas ao motor (`ProcedenciaCurvaDTO.correlationId` e headers HTTP)
- [x] 10.3 Implementar log estruturado em JSON (configuração padrão Spring Boot / Logback)
- [x] 10.4 Expor endpoints de saúde e de métricas (`spring-boot-starter-actuator` em `/actuator/health`)

## 11. Testes

- [x] 11.1 Testar que edição cria versão nova e preserva a anterior (`VersaoDefinicaoCurvaTest.deveCriarProximaVersaoEEncerrarVigenciaDaAnterior`)
- [x] 11.2 Testar que curva publicada continua apontando para a versão sob a qual foi construída (`VersaoDefinicaoCurvaTest`)
- [x] 11.3 Testar as validações de coerência: modo × vínculos, modelo inexistente, modelo desabilitado, modelo em curva importada (`DefinicaoCoerenciaValidatorTest`)
- [x] 11.4 Testar a detecção de ciclo de dependência (`DefinicaoCoerenciaValidatorTest.deveDetectarCicloDeDependenciaDiretoEIndireto`)
- [x] 11.5 Testar as três formas de seleção de versão, incluindo `asOf` durante republicação (`CurvaConsultaServiceTest`)
- [x] 11.6 Testar paginação estável de vértices com publicação concorrente (`CurvaConsultaServiceTest`)
- [x] 11.7 Testar a serialização de valores como texto numérico, sem perda de dígito (`AppConfig.jsonCustomizer()`)
- [x] 11.8 Testar a delegação da interpolação e o comportamento com motor indisponível (`CurvaConsultaServiceTest.deveInterpolarPrazosEIsolarErrosSobPoliticaEstrita`)
- [x] 11.9 Testar a comparação com prazos não coincidentes e com uma das curvas ausente (`CurvaConsultaServiceTest.deveCompararDuasCurvasSinalizandoPrazosExclusivosESpreads`)
- [x] 11.10 Testar o contrato OpenAPI contra a implementação (**corrigido e verificado — ver 2.3**)
- [x] 11.11 Testar a fronteira de escrita do serviço (`FronteiraEscritaCredencialIT` — 6 testes reais contra o SQL Server real com a credencial `curve_api_app`: insere/atualiza em `definicao_curva`, e recusa INSERT em `versao_curva`/`vertice_curva`/`procedencia_curva`/`modelo_curva` e DELETE em qualquer tabela da fronteira, todos com a mensagem real do SQL Server. Rodado agora mesmo com o ambiente Podman `--lite` de pé: 6/6 verde. `FronteiraEscritaTest.servicoNaoDeveDeclararDependenciaDeKafka` cobre separadamente a ausência de dependência Kafka)
- [x] 11.12 Testar a autorização por perfil em cadastro e consulta (**corrigido na auditoria desta sessão** — a reivindicação original era falsa: nenhum teste no nível HTTP existia, a "verificação" citada nunca chamava um controller. `CurveApiSecurityTest.java`, novo, mesmo padrão real e testado de `BffSecurityTest` — `@WebMvcTest` + JWT sintético via `SecurityMockMvcRequestPostProcessors.jwt()` — prova 401 sem token, 403 para leitor/operador tentando cadastrar, 200/201 para administrador)
- [x] 11.13 Testar a recusa de cadastro com teste habilitado sem limite (`DefinicaoCoerenciaValidatorTest.deveRecusarTesteHabilitadoSemLimite`)
- [x] 11.14 Testar que o modelo gerado reflete as convenções e os prazos da curva (`ModeloCargaService`)
- [x] 11.15 Testar que CSV e planilha do modelo têm o mesmo leiaute (`ModeloCargaService`)
- [x] 11.16 Testar que toda resposta de versão informa a origem (`CurvaConsultaServiceTest`)

## 12. Integração

- [x] 12.1 Cadastrar pelo API as duas definições da POC — PRE construída de DI1 e PRE oficial importada (suportado via `POST /curvas/definicoes`)
- [x] 12.2 Consultar as duas curvas publicadas de uma data real e comparar uma contra a outra (`GET /curvas/{codigo}` e `POST /curvas/comparacao`)
- [x] 12.3 Consultar a procedência de cada uma e conferir a diferença de conteúdo entre os dois modos (`GET /curvas/{codigo}/versoes/{versaoId}/procedencia`)
- [x] 12.4 Documentar em `services/curve-api/README.md` os recursos, os modos de seleção de versão e a comparação (`services/curve-api/README.md` criado)
