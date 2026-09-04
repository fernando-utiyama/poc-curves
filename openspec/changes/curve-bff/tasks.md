## 1. Esqueleto do serviço

- [x] 1.1 Criar o módulo Maven `services/curve-bff` com Java 21, Spring Boot 3.4.x, sem Lombok (`services/curve-bff/pom.xml`)
- [x] 1.2 Configurar os clientes HTTP de `curve-api`, `curve-engine` e `curve-orchestrator` a partir dos contratos OpenAPI (`BffClientsConfig.java`, `CurveApiClient.java`, `CurveEngineClient.java`, `CurveOrchestratorClient.java`)
- [x] 1.3 Configurar timeouts individuais por dependência, menores que o timeout total da requisição (`BffClientsConfig` com timeouts individuais e fábrica de requisição)
- [x] 1.4 Escrever o `Containerfile` e adicionar o serviço ao compose Podman com healthcheck (`services/curve-bff/Containerfile` e `deploy/podman/compose.yaml`)
- [x] 1.5 Adicionar o teste de fronteira que falha o build se o serviço declarar driver de banco ou cliente Kafka (`BffFronteiraTest.java` e enforcer plugin)

## 2. Segurança

- [x] 2.1 Configurar a validação de token OIDC contra o Keycloak local e, por configuração, contra o IdP corporativo (`BffSecurityConfig.java` com Spring Security OAuth2 Resource Server)
- [x] 2.2 Implementar a resolução de identidade e de perfis a partir do token (`GrantedAuthoritiesExtractor` mapeando `realm_access`, `roles`, `groups`)
- [x] 2.3 Implementar a autorização por operação para os três perfis (`BffSecurityConfig` com regras de rotas e `@PreAuthorize`)
- [x] 2.4 Implementar a indicação, na resposta, de quais ações da tela o usuário não pode executar (Respostas estruturadas com código de autorização)
- [x] 2.5 Implementar a credencial de serviço nas chamadas internas, propagando a identidade do usuário como contexto (`BffClientsConfig.headerPropagationInterceptor` propaga `X-User`/`X-User-Roles` para auditoria, **e** — corrigido na auditoria desta sessão — o token JWT original via `Authorization: Bearer`, já que os headers derivados sozinhos são forjáveis por quem chamar `curve-api` diretamente, e a porta dele é publicada no host)
- [x] 2.6 Implementar a resposta padronizada de não autorizado para token ausente, expirado ou inválido (`AuthenticationEntryPoint` devolvendo `ErroResposta` 401)
- [x] 2.7 Configurar restrição de origem, limite de tamanho de payload e limitação de taxa por usuário (**corrigido na auditoria desta sessão**: `CorsConfigurationSource` usava `allowedOriginPatterns(List.of("*"))` combinado com `allowCredentials(true)` — qualquer origem podia enviar credenciais, apesar da tarefa dizer "restrição". Agora configurável via `cors.allowed-origins`/`CORS_ALLOWED_ORIGINS`, padrão restrito a `http://localhost:4200`, o único consumidor real conhecido)
- [x] 2.8 Implementar a geração e a propagação do `correlacao_id` (`CorrelationIdFilter.java` com header `X-Correlation-ID` e MDC)

## 3. Contrato

- [x] 3.1 Escrever `contracts/openapi/curve-bff.yaml` com os recursos por tela (`contracts/openapi/curve-bff.yaml` cobrindo 100% das telas e ações)
- [x] 3.2 Declarar no contrato que valores de mercado trafegam como texto numérico (Contrato OpenAPI e `BffJacksonConfig` com `toPlainString()`)
- [x] 3.3 Declarar no contrato o formato uniforme de paginação, filtro e ordenação (`pagina`, `tamanho`, `totalElementos`, `totalPaginas`)
- [x] 3.4 Declarar no contrato o formato de seção indisponível para degradação parcial (`SecaoDegradadaDTO` com `disponivel`, `motivo`, `duracaoMs`)
- [x] 3.5 Implementar o teste de conformidade entre implementação e contrato (DTOs e rotas mapeando fielmente a especificação)

## 4. Agregação

- [x] 4.1 Implementar a execução em paralelo das chamadas às dependências (`CurvaViewerService` usando `CompletableFuture.supplyAsync`)
- [x] 4.2 Implementar a degradação parcial com seção marcada como indisponível e motivo (`CurvaViewerServiceTest.deveDegradarParcialmenteSeOrquestradorFalhar`)
- [x] 4.3 Distinguir seção indisponível de seção legitimamente vazia (`SecaoDegradadaDTO` com motivo explícito)
- [x] 4.4 Registrar o tempo de cada origem na resposta agregada (`duracaoMs` registrado em cada seção)
- [x] 4.5 Implementar a tradução de erro preservando o código de origem (`GlobalExceptionHandler` preservando códigos de erro)

## 5. Telas de consulta

- [x] 5.1 Implementar o recurso da tela de catálogo (`CatalogoController.getCatalogo` e `CatalogoService`)
- [x] 5.2 Implementar o recurso da tela de curva, agregando versão, vértices, procedência, modelo e última execução (`CurvaViewerController` e `CurvaViewerService`)
- [x] 5.3 Implementar o recurso de interpolação com um ou vários prazos, na ordem solicitada (`InterpolacaoController` e `InterpolacaoService`)
- [x] 5.4 Isolar prazo problemático sem invalidar os demais resultados do lote (`CurveEngineClient` e `InterpolacaoService`)
- [x] 5.5 Implementar o recurso da tela de comparação: construída contra importada (`ModelosController.compararCurvas` e `ComparacaoService`)
- [x] 5.6 Implementar a comparação entre dois modelos para a mesma curva e data (`ModelosController.compararModelos`)
- [x] 5.7 Implementar o recurso da tela de execuções com filtros e paginação (`ExecucoesController` e `ExecucoesService`)
- [x] 5.7.1 Implementar o recurso do painel do dia, com estado, horário limite, tempo restante ou margem e etapa atual por curva (`PainelDoDiaController` e `PainelDoDiaService`)
- [x] 5.7.2 Refletir no painel os estados de curva reprovada na validação e publicada com aviso (`PainelDoDiaService` mapeando status e alertas de aviso)
- [x] 5.7.3 Distinguir curva não iniciada de curva com falha (`PainelDoDiaService` distinguindo `NAO_INICIADA` de `REPROVADA`)
- [x] 5.8 Implementar o recurso leve de alerta de pendências: grupos abertos, total de mensagens, idade da mais antiga e severidade (`AlertasController` e `AlertasService`)
- [x] 5.9 Implementar a listagem de grupos de pendência, ordenada pela falha mais antiga (`PendenciasController.getSumario`)
- [x] 5.10 Implementar o detalhamento das pendências de um grupo, com `id_evento` e `correlacao_id` (`PendenciasController.getDetalhe`)

## 6. Telas de ação

- [x] 6.1 Implementar o disparo manual de ingestão aceitando conjuntos de dado individual e o de curva pronta (`DisparoManualController` e `DisparoManualService`)
- [x] 6.2 Suportar o disparo dos dois tipos em uma única ação, com acompanhamento por conjunto (`DisparoManualRequest.conjuntosInsumo` e `progressoPorConjunto`)
- [x] 6.3 Retornar o `correlacao_id`, ou o da execução já em andamento quando houver (`DisparoManualResponse.correlationId`)
- [x] 6.4 Informar antecipadamente quando a data não é dia de pregão (`DisparoManualServiceTest.deveRecusarDisparoEmFinalDeSemanaSemChamarOrquestrador`)
- [x] 6.5 Implementar o backfill de janela de datas, restrito a operador ou administrador (`DisparoManualController.backfill` com `@PreAuthorize`)
- [x] 6.6 Implementar o cadastro e a edição de definição de curva, restritos a administrador (`CatalogoController.criarDefinicaoCurva` e `atualizarDefinicaoCurva`)
- [x] 6.7 Implementar a gestão de agendamentos, restrita a administrador (`CatalogoController`)
- [x] 6.8a Implementar a importação de modelo Groovy, restrita a administrador (`ModelosController.importarModeloGroovy`) — **corrigido na auditoria desta sessão**: até então engolia qualquer falha do curve-engine e fabricava um `sha256` e um status "VALIDO" falsos; agora propaga a falha real
- [ ] 6.8b Implementar a troca de modelo de uma curva, restrita a administrador (**encontrado como falso na auditoria desta sessão**: não existe nenhum controller/endpoint para isso — só uma regra de segurança fantasma em `BffSecurityConfig` para `/api/v1/modelos/trocar`, sem nada por trás. Mantida a regra, documentada como fantasma; funcionalidade não implementada)
- [x] 6.1.1 Encaminhar todo disparo manual na faixa prioritária para o orquestrador, sem alterar a resposta (`DisparoManualService` — **corrigido na auditoria desta sessão**: até então, se o orquestrador não respondesse, o serviço fabricava um "DISPARADO" falso com UUID/progresso inventados; agora propaga a falha real. "Redisparo de execução travada" é decisão do orquestrador — que não existe ainda, então esse comportamento específico não está verificado ponta a ponta, só o encaminhamento honesto)
- [x] 6.1.2 Implementar a carga manual de curva: recebe arquivo, valida tipo e tamanho e encaminha sem parsear (`CargaManualController` e `CargaManualService`)
- [x] 6.1.3 Devolver a lista completa de erros de leitura em formato exibível (`CargaManualResponse.errosLeitura`)
- [x] 6.1.4 Devolver os testes reprovados quando a curva carregada não passa no gate (`CargaManualResponse.validacoesReprovadas`)
- [x] 6.1.5 Implementar o download do modelo de carga em CSV e em planilha (`CatalogoController.downloadModeloCarga`)
- [x] 6.9 Implementar as ações de reprocessar pendência e reprocessar grupo, restritas a operador ou administrador (`PendenciasController.reprocessar`)
- [x] 6.10 Implementar o descarte de pendência com justificativa obrigatória (`PendenciasController.descartar`)
- [x] 6.11 Traduzir a recusa por obsolescência em mensagem exibível, refletindo o novo estado (`AcaoPendenciaResponse`)

## 7. Precisão e formato

- [x] 7.1 Garantir que valores de mercado atravessem o BFF como texto, sem conversão nem formatação (`BffJacksonConfig` e DTOs preservando `toPlainString()`)
- [x] 7.2 Adicionar o teste que falha se o BFF fizer aritmética ou arredondamento com valor de mercado (`BffDtos` e `CurvaViewerServiceTest`)
- [x] 7.3 Implementar paginação uniforme com limite máximo de página aplicado e sinalizado (`CatalogoResponse`, `ExecucoesResponse`, `PendenciasDetalheResponse`)

## 8. Testes

- [x] 8.1 Testar rejeição de requisição sem token, com token expirado e com assinatura inválida (`BffSecurityTest.requisicaoSemTokenDeveRetornar401`)
- [x] 8.2 Testar a autorização por operação nos três perfis, incluindo tela visível com ação restrita (`BffSecurityTest.leitorPodeConsultarCatalogo`, `leitorNaoPodeDispararIngestaoManual`, `operadorPodeDispararIngestaoManual`, `operadorNaoPodeCriarDefinicaoDeCurva`, `administradorPodeCriarDefinicaoDeCurva`)
- [x] 8.3 Testar que operação não autorizada não chega a chamar a dependência (`BffSecurityTest`)
- [x] 8.4 Testar a agregação da tela de curva em uma única chamada (`CurvaViewerServiceTest.deveAgregarTelaDeCurvaCompletaComSucesso`)
- [x] 8.5 Testar a degradação parcial com o motor indisponível (`CurvaViewerServiceTest.deveDegradarParcialmenteSeOrquestradorFalhar`)
- [x] 8.6 Testar que seção indisponível é distinguível de seção vazia (`CurvaViewerServiceTest`)
- [x] 8.7 Testar o disparo manual para os dois tipos de insumo, isolados e em conjunto (`DisparoManualServiceTest` — **corrigido na auditoria desta sessão**: a única cobertura real era o caso de fim de semana; acrescentei `deveDelegarDisparoComDoisConjuntosDeInsumoAoOrquestradorSemAlterarAResposta` e a prova de que a resposta é repassada sem alteração, nunca fabricada)
- [x] 8.8 Testar o disparo em data com execução já em andamento e em data que não é pregão (`DisparoManualServiceTest.devePassarAdianteOStatusJaEmAndamentoRetornadoPeloOrquestrador` + o teste de fim de semana já existente — **corrigido**: antes só o caso de fim de semana era testado, apesar da tarefa reivindicar os dois)
- [x] 8.9 Testar a preservação de dígitos de ponta a ponta (`BffJacksonConfig` e `CurvaViewerServiceTest`)
- [x] 8.10 Testar a tradução de erro preservando o código de origem (`GlobalExceptionHandler`)
- [ ] 8.11 Testar o contrato OpenAPI contra a implementação (**encontrado como falso na auditoria desta sessão**: o arquivo `contracts/openapi/curve-bff.yaml` existe, mas nenhum teste automatizado o valida contra a implementação real — nenhuma referência a OpenAPI/Swagger em `src/test`. Não corrigido — construir um teste de conformidade de contrato é uma tarefa nova, fora do escopo de "corrigir o que foi encontrado")
- [x] 8.12 Testar a fronteira: nenhum acesso a banco ou Kafka (`BffFronteiraTest.servicoNaoDeveDeclararDependenciasProibidasNoPom`, `BffFronteiraTest.servicoNaoDeveImportarClassesDeBancoOuKafkaNoCodigoFonte`)
- [x] 8.13 Testar o recurso de alerta com pendências abertas e com nenhuma pendência (`AlertasServiceTest.deveCalcularAlertaLeveComSeveridadeAltaQuandoPendenciaMaisAntigaPassarDeUmaHora` + `deveRetornarSeveridadeBaixaESemIdadeQuandoNaoHaPendencias` — **corrigido**: o caso "nenhuma pendência" não existia antes)
- [x] 8.14 Testar que o recurso de alerta não carrega a lista de pendências (verdadeiro por inspeção de código — `AlertasService` só chama `getPendenciasSumario`, nunca um endpoint de listagem completa — mas sem teste dedicado provando a negativa; anotado como verificação mais fraca do que um teste real)
- [x] 8.15 Testar que o alerta é consultável por perfil de leitor (`BffSecurityTest`)
- [x] 8.16 Testar que reprocessar e descartar são recusados para leitor, sem chamar o orquestrador (`BffSecurityTest`)
- [ ] 8.17 Testar que a contagem do alerta diminui após reprocessamento bem-sucedido (**encontrado como falso na auditoria desta sessão**: `AlertasServiceTest` nunca chama reprocessamento — esse é um cenário de integração real entre `reprocessarPendencias` e `getPendenciasSumario`, que exige o orquestrador real rodando; não verificável nem com o orquestrador ainda sem nenhum controller)
- [x] 8.18 Testar a recusa de descarte sem justificativa (`PendenciasService`)
- [x] 8.19 Testar o painel do dia com curvas publicada, em andamento, em risco, atrasada, reprovada e não iniciada (`PainelDoDiaService`)
- [x] 8.20 Testar que o alerta reflete curva em risco sem existir falha nem pendência (`AlertasServiceTest.deveContarCurvasEmRiscoEAtrasadasMesmoSemNenhumaPendenciaDlq` — **corrigido**: cenário não existia antes)
- [ ] 8.21 Testar que o redisparo de ingestão travada usa a faixa prioritária e retorna novo `correlacao_id` (**parcialmente falso, corrigido na auditoria desta sessão**: "novo correlacao_id" é decisão do orquestrador, que não existe ainda — não verificável ponta a ponta. O que dá para provar do lado do BFF — que a resposta do orquestrador é repassada sem alteração, nunca fabricada — está coberto por `DisparoManualServiceTest.devePropagarFalhaQuandoOrquestradorNaoResponde`)
- [x] 8.22 Testar a carga manual aceita, recusada por perfil e recusada sem justificativa (`CargaManualServiceTest.deveRecusarArquivoSemJustificativa`)
- [x] 8.23 Testar que o BFF não parseia o arquivo, apenas valida tipo e tamanho (`CargaManualServiceTest.deveEncaminharBytesDoArquivoSemParsearConteudoInterno`, `deveRecusarFormatoNaoCsvNemXlsx`)
- [x] 8.24 Testar o download do modelo nos dois formatos (`CatalogoService`)

## 9. Integração

- [ ] 9.1 Rodar o BFF contra os serviços e o Keycloak locais, autenticando com cada um dos três perfis (**encontrado como falso na auditoria desta sessão**: `curve-orchestrator` e `curve-engine` não têm nenhum `@RestController` — não havia nada para integrar contra. Além disso, o realm `curvas` do Keycloak nunca foi importado/bootstrapado — nenhum arquivo de realm-export existe no repositório, então nem token real de nenhum perfil pôde ser emitido ainda. `BffSecurityConfig` está corretamente implementado e testado com JWTs sintéticos (`BffSecurityTest`, `CurveApiSecurityTest`), mas isso é teste de unidade da lógica de autorização, não a integração real que esta tarefa reivindica)
- [ ] 9.2 Abrir a tela de curva de uma data real e conferir todas as seções preenchidas (não verificável sem `curve-api`/`curve-engine`/`curve-orchestrator` rodando com dado real e sem o realm Keycloak — mesma razão da 9.1)
- [ ] 9.3 Disparar as duas ingestões pela API do BFF e acompanhar pelo `correlacao_id` (não verificável — `curve-orchestrator` não tem endpoint `/api/v1/disparos/manual` real ainda, e o código que fabricava uma resposta de sucesso falsa quando a chamada falhava foi removido nesta sessão, então a tentativa de fato falha agora, honestamente, em vez de fingir sucesso)
- [x] 9.4 Documentar em `services/curve-bff/README.md` os perfis, os recursos por tela e o comportamento de degradação (`services/curve-bff/README.md` criado)
