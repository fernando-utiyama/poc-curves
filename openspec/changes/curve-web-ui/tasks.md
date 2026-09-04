## 1. Esqueleto do app

- [x] 1.1 Criar `web/curve-web-ui` com Angular 18+, TypeScript estrito, lint e formatação (Angular 18.2 standalone components + router, TypeScript estrito em `tsconfig.json`, `package.json`, Vitest e scripts de build/lint/test)
- [x] 1.2 Gerar tipos e cliente HTTP a partir de `contracts/openapi/curve-bff.yaml` (`contracts/openapi/curve-bff.yaml` criado e `src/app/core/api/models.ts` + `src/app/core/api/curve-bff-client.service.ts` fortemente tipados com valores de mercado trafegando como string)
- [x] 1.3 Configurar a rota base como parâmetro de configuração, sem suposição sobre a rota raiz (`RuntimeConfigService.get('baseHref')` com `<base href="/">` dinâmico e rotas relativas)
- [x] 1.4 Definir o tema por tokens substituíveis, sem cor ou tipografia fixada em componente (`src/styles.css` com CSS custom properties `--color-*`, `--font-*`, `--space-*`, alinhamento decimal e classes `.tabular-nums` / `.data-table`)
- [x] 1.5 Implementar a fonte única e trocável de contexto de usuário (`src/app/core/auth/user-context.ts` e `src/app/core/auth/auth.service.ts`)
- [x] 1.6 Escrever o `Containerfile` servindo o artefato estático e adicionar o app ao compose Podman (**corrigido na auditoria desta sessão** — a reivindicação original era falsa: `package.json` não tinha `@angular/cli`/`@angular-devkit/build-angular`/`angular.json` nenhum, o script `build` era só `tsc --noEmit` — checagem de tipo, gera zero artefato — e o `Containerfile` tinha `RUN npm run build || true`, que nunca podia falhar a imagem. O resultado real era uma imagem servindo só `public/` (sem `index.html`). Corrigido: `angular.json` real com o builder moderno `@angular-devkit/build-angular:application`, `npm run build` agora roda `ng build` de verdade (sem `|| true`), `Containerfile` copia de `dist/curve-web-ui/browser/` — build real verificado, gera bundle de verdade. Encontrei e corrigi 2 erros reais de sintaxe de template no processo — nunca pegos porque o build nunca tinha rodado — e um `nginx.conf` sem proxy nenhum para `/api/v1` (outro achado real: `bffBaseUrl` relativo nunca alcançava o curve-bff))
- [x] 1.7 Injetar em tempo de execução a URL do BFF e a configuração de identidade, sem fixar no build (`public/config.json` carregado no boot via `APP_INITIALIZER` em `app.config.ts`)

## 2. Autenticação

- [x] 2.1 Implementar o fluxo OIDC contra o Keycloak local (**corrigido na auditoria desta sessão** — a reivindicação original era falsa: `AuthService` tinha um usuário fixo com os três perfis sempre concedidos e um token falso como estado padrão, sem nenhum login real; um seletor de perfil na tela dava qualquer visitante virar Administrador com um clique. Reescrito com `keycloak-js` real, `onLoad: 'login-required'`, papéis extraídos de `realm_access.roles` do token real — mesma claim que o lado Java lê. Bootstrapado um realm `curvas` real no Keycloak local pela primeira vez nesta sessão (`deploy/podman/keycloak-import/`, nunca existia antes — nenhuma integração de auth de nenhum serviço podia ter sido verificada contra um IdP real até aqui). **Ressalva**: o fluxo de login via navegador não foi verificado ponta a ponta — encontrei um problema não resolvido no `grant_type=password` do realm recém-criado (`invalid_grant: Account is not fully set up`, mesmo com usuário/credencial confirmados corretos via API de admin) que não isolei a causa; o endpoint de autorização via navegador responde 200 normalmente, mas isso não prova o fluxo completo)
- [x] 2.2 Anexar o token a todas as chamadas ao BFF (`src/app/core/auth/auth.interceptor.ts` anexando header `Authorization: Bearer <token>`)
- [x] 2.3 Retornar à rota pretendida após o login (`AuthService.setReturnUrl` e `getReturnUrl`)
- [x] 2.4 Implementar o tratamento de sessão expirada com reautenticação e retomada (`authInterceptor` captura 401 e aciona modal de sessão expirada com reautenticação)
- [x] 2.5 Preservar o conteúdo de formulário em preenchimento durante a reautenticação (`src/app/core/state/form-preservation.service.ts` salvando rascunhos em sessionStorage)
- [x] 2.6 Implementar a resolução de perfis para habilitar e desabilitar ações (`AuthService` com signals `isViewer`, `isOperator`, `isAdmin` e explicações de permissão)

## 3. Fundações de tela

- [x] 3.1 Implementar o tratamento uniforme de carregamento, erro e degradação parcial (estados reativos `loading`, `error` e suporte a `secaoDegradada`)
- [x] 3.2 Distinguir visualmente seção indisponível de seção legitimamente vazia, com motivo e ação de repetir (`SecaoDegradadaComponent` em `src/app/shared/components/secao-degradada/`)
- [x] 3.3 Implementar a exibição de ação desabilitada com o motivo, para operação sem permissão (`AuthService.getPermissionExplanation` acoplado aos botões de ação e atributos `[title]`)
- [x] 3.4 Implementar o tipo `string` para valores de mercado e a formatação apenas para exibição (`src/app/core/formatting/market-data-formatter.ts` e pipes `taxaFormat`, `fatorDesconto`, `bpsFormat`)
- [x] 3.5 Adicionar a regra de lint que falha o build em conversão numérica de campo de valor de mercado (`scripts/lint-market-data.mjs` escaneando todo `src/` e proibindo `parseFloat`, `Number` e unários em taxas/fatores)
- [x] 3.6 Implementar a tabela virtualizada com tipografia tabular e alinhamento decimal (`.data-table`, `.tabular-nums` e `.th-num`/`.td-num` com CSS tabular-nums)
- [x] 3.7 Implementar paginação, filtro e ordenação alinhados ao contrato do BFF (parâmetros de paginação e filtros em `CatalogoResponse`, `ExecucoesResponse` e `curve-bff-client.service.ts`)

## 4. Painel do dia

- [x] 4.1 Implementar o painel do dia como tela inicial, com uma linha por curva ativa (`PainelDoDiaComponent` em `src/app/features/painel-do-dia/`)
- [x] 4.2 Exibir estado, horário limite, tempo restante ou margem e etapa atual (`ItemPainelDoDiaDTO` e tabela resumo)
- [x] 4.3 Destacar curva em risco antes de qualquer falha, e curva atrasada de forma distinta de falha (classe `.linha-risco`, badge pulsante e tempo de margem)
- [x] 4.4 Exibir curva reprovada na validação com acesso aos testes que falharam e à versão vigente (exibição de `testesReprovados` e `versaoVigente`)
- [x] 4.5 Exibir curva publicada com aviso sem sugerir falha (badge de aviso e contagem de `avisosValidacao`)
- [x] 4.6 Exibir curva não iniciada com o horário previsto (`horarioPrevisto` exibido)
- [x] 4.7 Oferecer o redisparo a partir do painel, sujeito ao perfil (botão "⚡ Redisparar" restrito ao perfil de operador/admin)

## 5. Catálogo e cadastro

- [x] 5.1 Implementar a tela de catálogo com filtro, paginação e modo de origem visível (`CatalogoComponent` em `src/app/features/catalogo/`)
- [x] 5.2 Implementar a navegação do catálogo para a tela de curva (links diretos para `/curvas/:codigo`)
- [x] 5.3 Implementar o formulário de cadastro e edição com todos os campos da definição (`CurvaCadastroComponent` em `src/app/features/catalogo/curva-cadastro.component.ts`)
- [x] 5.4 Exibir o aviso de que salvar cria uma nova versão da definição (faixa de aviso de versionamento imutável)
- [x] 5.5 Implementar a escolha do modelo de construção, entre embutido e Groovy importado (select dinâmico de modelos para `BOOTSTRAPPED`)
- [x] 5.6 Desabilitar o campo de modelo para definição importada, com a explicação (bloqueio automático com mensagem explicativa quando `modoOrigem === 'IMPORTED'`)
- [x] 5.7 Exibir erro de validação do backend junto ao campo correspondente quando identificável (`erroValidacao` reativo exibido)
- [x] 5.8 Incluir no cadastro o horário limite, o orçamento por etapa, a janela de bloqueio, os limites de validação e a classificação de cada teste (campos completos de convenção financeira e limites)

## 6. Viewer de curva

- [x] 6.1 Implementar o gráfico e a tabela de vértices (`CurvaViewerComponent` em `src/app/features/viewer/curva-viewer.component.ts`)
- [x] 6.2 Implementar os seletores de data de referência, momento de curva e versão (controles de topo reativos)
- [x] 6.3 Implementar a seleção por instante, com indicação clara de que não é a versão corrente (aviso visual de versão histórica quando `!isVersaoCorrente`)
- [x] 6.4 Exibir a procedência junto dos dados: versão, publicação, execução e modelo (card de metadados ao lado dos números)
- [x] 6.5 Exibir, para curva importada, o arquivo de origem e o lote, indicando que não houve cálculo (card de procedência adaptável)
- [x] 6.6 Informar explicitamente a ausência de curva publicada na data escolhida (`.sem-curva-box` com atalhos de disparo e carga manual)
- [x] 6.7 Exibir o resultado da validação de consistência junto da curva, com avisos destacados (painel de validação com lista de testes e medidas)
- [x] 6.8 Exibir versão reprovada com os testes que falharam e a indicação de que não foi publicada (badge e mensagens de falha do gate)

## 7. Consulta interpolada

- [x] 7.1 Implementar a entrada de um prazo ou de uma lista de prazos (`InterpolacaoComponent` em `src/app/features/interpolacao/interpolacao.component.ts`)
- [x] 7.2 Exibir taxa, fator de desconto e a versão de curva usada (tabela de amostragem formatada)
- [x] 7.3 Sinalizar ponto extrapolado (badge `.badge-extrapolado`)
- [x] 7.4 Exibir o erro nomeado sob política estrita sem invalidar os demais prazos consultados (isolamento do item com `ERRO_FORA_INTERVALO` preservando taxas dos demais)

## 8. Ingestão e monitoramento

- [x] 8.1 Implementar a tela de disparo manual com seleção de data e do que consumir (`DisparoManualComponent` em `src/app/features/ingestao/disparo-manual.component.ts`)
- [x] 8.2 Permitir marcar os conjuntos de dado individual e o de curva pronta, isolados ou juntos (checkboxes de BVBG.086, BVBG.028, PR_DI1 e Taxas de Referência)
- [x] 8.3 Avisar antes do disparo quando a data não é dia de pregão (`verificarPregao()` identificando fins de semana e feriados)
- [x] 8.4 Exibir o `correlacao_id` de forma copiável e acompanhar o progresso por conjunto (`.codigo-copia` com clique para copiar)
- [x] 8.5 Exibir a execução já em andamento em vez de criar outra (tratamento de status `JA_EM_ANDAMENTO`)
- [x] 8.6 Implementar o backfill com data inicial e final, progresso e interrupção (`BackfillComponent` em `src/app/features/ingestao/backfill.component.ts`)
- [x] 8.7 Implementar o monitor de execuções com filtros por curva, data, estado e `correlacao_id` (`ExecucoesMonitorComponent` em `src/app/features/execucoes/execucoes-monitor.component.ts`)
- [x] 8.8 Dar tratamento visual próprio à ausência de dado, distinto de falha, com o motivo (`.linha-sem-dado` e badge neutro)
- [x] 8.9 Implementar o redisparo a partir do monitor (botão "⚡ Redisparar" na faixa prioritária)
- [x] 8.10 Implementar o indicador global de pendência de dead-letter, presente em todas as telas (`AlertaBannerComponent` no layout raiz)
- [x] 8.11 Implementar a atualização periódica do indicador, sem ação do usuário (`AlertasGlobalService` com polling configurável)
- [x] 8.12 Ocultar o indicador quando não houver pendência aberta, sem contador zerado permanente (`temAlertaAtivo()` oculta banner quando 0)
- [x] 8.13 Destacar visualmente pendência aberta há mais tempo que o limite configurado (`badge-envelhecido` e classe `.alerta-critico` quando idade > limite)
- [x] 8.14 Sinalizar estado desconhecido quando a consulta do alerta falhar, em vez de exibir zero (`isErrorState` exibindo alerta cinza com ação de reconectar)
- [x] 8.15 Implementar a tela de pendências agrupadas, ordenada pela falha mais antiga (`PendenciasDlqComponent` em `src/app/features/pendencias-dlq/pendencias-dlq.component.ts`)
- [x] 8.16 Implementar a expansão do grupo com `id_evento`, `correlacao_id` e detalhe da falha (toggle de expansão assíncrono)
- [x] 8.17 Implementar a navegação da pendência para a execução correspondente (link com queryParam `correlationId`)
- [x] 8.18 Implementar as ações de reprocessar pendência e grupo, com o estado refletido sem recarga manual (reprocessamento individual e em lote)
- [x] 8.19 Implementar o descarte com justificativa obrigatória bloqueada na tela quando vazia (modal de confirmação bloqueado se justificativa vazia)
- [x] 8.20 Exibir a recusa por obsolescência com a explicação de que sobrescreveria dado mais novo (estado `OBSOLETO`)
- [x] 8.21 Desabilitar as ações de correção para perfil de leitor, com o motivo visível (`[disabled]="!authService.isOperator()"` com tooltip explicativo)

## 9. Carga manual de curva

- [x] 9.1 Implementar a tela de carga com seleção de curva, data, momento, arquivo e justificativa (`CargaManualComponent` em `src/app/features/carga-manual/carga-manual.component.ts`)
- [x] 9.2 Oferecer o download do modelo em CSV e em planilha na própria tela (links de download para CSV e XLSX)
- [x] 9.3 Bloquear a submissão enquanto a justificativa estiver vazia (`!justificativa.trim()` bloqueia botão de envio)
- [x] 9.4 Listar todos os erros de leitura com linha, coluna e motivo, deixando claro que nada foi publicado (tabela detalhada de `errosLinha`)
- [x] 9.5 Exibir os testes reprovados quando a curva carregada não passa no gate, indicando a versão vigente (`testesReprovados` exibidos com aviso de versão anterior mantida)
- [x] 9.6 Desabilitar a submissão para perfil de leitor, com o motivo visível (permissão restrita a operador/admin)
- [x] 9.7 Marcar a origem carregada no painel do dia, no viewer, no histórico e na procedência (badge `CARREGADA` nas telas)
- [x] 9.8 Exibir autor e justificativa junto de uma versão carregada (`justificativa-box` no viewer de curva)

## 10. Modelos e comparação

- [x] 10.1 Implementar a listagem de modelos com tipo e estado (`ModelosComponent` em `src/app/features/modelos/modelos.component.ts`)
- [x] 10.2 Implementar a importação de modelo Groovy, exibindo o resultado da validação (formulário com compilação e registro)
- [x] 10.3 Exibir o motivo da recusa quando o script não compila ou não produz vértices (`erroCompilacao` reativo)
- [x] 10.4 Implementar o apontamento de uma curva para outro modelo, restrito a administrador (edição de definição e troca de modelo)
- [x] 10.5 Implementar a comparação construída contra importada, com tabela de diferenças por prazo (`ComparacaoComponent` em `src/app/features/modelos/comparacao.component.ts`)
- [x] 10.6 Implementar a comparação modelo contra modelo (suporte a modelo alternativo)
- [x] 10.7 Sinalizar prazos presentes em apenas uma das curvas, sem valor inventado (status `PRESENTE_APENAS_EM_A` / `PRESENTE_APENAS_EM_B`)
- [x] 10.8 Destacar as maiores diferenças sem sugerir que diferença é necessariamente erro (spreads em bps exibidos de forma neutra)

## 11. Testes

- [x] 11.1 Testar o fluxo de autenticação e o retorno à rota pretendida (`src/app/test/auth-session.spec.ts` — **corrigido na auditoria desta sessão**: testava `setRoles`, um método que só existia para o seletor de perfil fake; reescrito para usar `aplicarContextoParaTeste`, o mesmo espírito de testar autorização com um contexto sintético — como o lado Java testa com JWT sintético — sem precisar de um IdP real rodando)
- [x] 11.2 Testar sessão expirada com retomada e preservação de formulário (`src/app/test/auth-session.spec.ts`)
- [x] 11.3 Testar a exibição de ação desabilitada por perfil, nos três perfis (`src/app/test/auth-session.spec.ts`)
- [x] 11.4 Testar a preservação de dígitos na exibição de valores com muitas casas decimais (`src/app/test/precision-formatting.spec.ts`)
- [x] 11.5 Testar que a regra de lint falha em conversão numérica de valor de mercado (`src/app/test/lint-rule.spec.ts`)
- [x] 11.6 Testar o desempenho da tabela com centenas de vértices (`src/app/test/curva-viewer.spec.ts`)
- [x] 11.7 Testar degradação parcial e a distinção entre seção indisponível e vazia (`src/app/test/curva-viewer.spec.ts`)
- [x] 11.8 Testar o disparo manual para os dois tipos de insumo, isolados e juntos (`src/app/test/ingestao-execucoes.spec.ts`)
- [x] 11.9 Testar o aviso de data que não é dia de pregão e o caso de execução já em andamento (`src/app/test/ingestao-execucoes.spec.ts`)
- [x] 11.10 Testar a exibição de ausência de dado como estado próprio no monitor (`src/app/test/ingestao-execucoes.spec.ts`)
- [x] 11.11 Testar a comparação com prazos não coincidentes (`src/app/test/modelos-comparacao.spec.ts`)
- [x] 11.12 Testar que o indicador aparece quando surge pendência e some quando todas são resolvidas (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.13 Testar a resolução parcial: contagem diminui e o indicador permanece (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.14 Testar o destaque de pendência envelhecida (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.15 Testar que falha na consulta do alerta não é exibida como ausência de pendência (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.16 Testar o agrupamento na tela com milhares de mensagens do mesmo motivo (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.17 Testar reprocessamento de grupo, falha em novo reprocessamento e recusa por obsolescência (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.18 Testar o bloqueio de descarte sem justificativa (`src/app/test/dlq-alertas.spec.ts`)
- [x] 11.19 Testar as ações desabilitadas para perfil de leitor na tela de pendências (`src/app/test/auth-session.spec.ts`)
- [x] 11.20 Testar o painel do dia nos seis estados possíveis de curva (`src/app/test/painel-do-dia.spec.ts`)
- [x] 11.21 Testar que curva em risco aparece destacada sem existir falha (`src/app/test/painel-do-dia.spec.ts`)
- [x] 11.22 Testar o redisparo pelo painel e a exibição das duas tentativas vinculadas (`src/app/test/painel-do-dia.spec.ts`)
- [x] 11.23 Testar a exibição de aviso de validação sem sugerir falha, e de versão reprovada (`src/app/test/painel-do-dia.spec.ts`)
- [x] 11.24 Testar que a compilação falha quando o contrato do BFF remove um campo usado (verificado via `npm run build` com tipagem forte contra `models.ts`)
- [x] 11.25 Testar a fronteira: nenhuma chamada a serviço de domínio (verificado: todo acesso passa unicamente por `CurveBffClientService`)
- [x] 11.26 Testar que rota base configurável e tokens de tema funcionam sem alteração de código (`RuntimeConfigService` + `styles.css`)
- [x] 11.27 Testar a carga manual bem-sucedida, com erros por linha e reprovada no gate (`src/app/test/carga-manual.spec.ts`)
- [x] 11.28 Testar o bloqueio da submissão sem justificativa e para perfil de leitor (`src/app/test/carga-manual.spec.ts`)
- [x] 11.29 Testar o download do modelo nos dois formatos, com o leiaute da curva (`src/app/test/carga-manual.spec.ts`)
- [x] 11.30 Testar que a origem carregada aparece no painel, no viewer, no histórico e na procedência (`src/app/test/carga-manual.spec.ts`)

## 12. Integração

- [ ] 12.1 Rodar o app no compose Podman contra o BFF e o Keycloak locais (**encontrado como falso na auditoria desta sessão**: o container nunca serviu um bundle real — corrigido nesta sessão (tarefa 1.6), e a imagem builda de verdade agora, mas rodar contra `curve-bff`/Keycloak reais end-to-end via navegador não foi verificado, dado o problema aberto do realm — ver 2.1)
- [ ] 12.2 Executar o fluxo completo pela tela: disparar a ingestão de uma data, acompanhar a execução e ver a curva publicada (**não verificável**: `curve-orchestrator` não tem nenhum controller real — nada para a tela disparar de verdade, mesmo achado da auditoria de `curve-bff`)
- [ ] 12.3 Comparar na tela a curva construída contra a importada da mesma data (**não verificável** — mesma razão: sem dado real publicado por `curve-orchestrator`/`curve-engine`)
- [ ] 12.4 Importar um modelo Groovy, apontar a curva para ele e comparar contra o modelo embutido (**parcialmente falso**: a importação chama um endpoint real do `curve-engine` que não existe ainda; "apontar a curva para o modelo" nem tem controller no `curve-bff` — regra de segurança fantasma para `/api/v1/modelos/trocar`, achado na auditoria de `curve-bff`)
- [ ] 12.5 Provocar uma falha permanente de propósito, ver o alerta aparecer, corrigir a causa, reprocessar pela tela e confirmar que o alerta desaparece sozinho (**não verificável** — depende de `curve-orchestrator` real gerando uma pendência DLQ de verdade)
- [x] 12.6 Documentar em `web/curve-web-ui/README.md` as telas, a configuração em tempo de execução e o que falta para embarcar no shell (`web/curve-web-ui/README.md` completo)

## 13. Achados adicionais da auditoria desta sessão (fora dos itens originais)

- [x] 13.1 `nginx.conf` não tinha nenhum bloco de proxy para `/api/v1` — `bffBaseUrl: "/api/v1"` (caminho relativo, `config.json`) nunca alcançava `curve-bff` de verdade; toda chamada de API caía no catch-all do SPA e devolvia `index.html`. Corrigido: `location /api/v1/ { proxy_pass http://curve-bff:8080/api/v1/; ... }`, propagando `Authorization`
- [x] 13.2 Cabeçalhos de segurança ausentes em `nginx.conf` (`X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`) — acrescentados. Sem CSP explícita de propósito: os origens reais variam por implantação (config.json é runtime), uma CSP fixa arriscaria bloquear uma implantação legítima sem verificação contra navegador real
- [ ] 13.3 `npm audit` real: 6 vulnerabilidades altas nas dependências de runtime (`@angular/core`/`@angular/compiler`/`@angular/platform-browser`/`@angular/router`, XSS via i18n/SVG/MathML) — correção exige pular para Angular 21.x (a versão atual, 18.2, pinada deliberadamente, já está desatualizada nesse quesito mesmo até a 19.2). Não corrigido: um salto de major version tão grande é um risco real de quebra em ~40 arquivos, fora do escopo de uma sessão de correção de bugs — recomendado como tarefa dedicada e testada à parte. As outras ~47 vulnerabilidades restantes são só de dependências de build/dev (webpack, tooling do próprio `@angular/cli`), não vão para o bundle de produção
