## Context

O BFF existe por duas razões independentes que apontam para a mesma peça. A primeira é de produto: as telas de curva precisam de dados que vivem em três serviços diferentes, e montar isso no navegador significa cinco chamadas em cascata, latência somada e um front que conhece a topologia interna da plataforma. A segunda é de segurança: expor `curve-api`, `curve-engine` e `curve-orchestrator` ao navegador criaria três superfícies públicas, cada uma reimplementando autorização.

O BFF é, portanto, ao mesmo tempo tradutor e porteiro. Ele não tem domínio próprio — não calcula, não persiste, não decide nada sobre curva. O que ele tem é conhecimento de tela e responsabilidade de fronteira.

Contexto de precisão: valores de mercado trafegam como texto numérico desde as APIs de domínio. O BFF preserva isso até o navegador; converter para número aqui reintroduziria exatamente a perda que a decisão evita.

## Goals / Non-Goals

**Goals:**

- Ser a única superfície exposta ao navegador, com autenticação e autorização centralizadas.
- Entregar cada tela em uma chamada, com o que ela precisa e nada além.
- Degradar parcialmente: uma dependência fora do ar não deve apagar a tela inteira.
- Preservar precisão numérica ponta a ponta.

**Non-Goals:**

- Ter lógica de domínio, cache de negócio ou estado próprio.
- Acessar banco ou Kafka — sob nenhuma circunstância.
- Ser uma API genérica reutilizável por outros consumidores; ele serve a este front.
- Reimplementar validação de domínio; erros de negócio vêm das APIs e são traduzidos, não recriados.

## Decisions

### D1 — Um endpoint por tela ou por ação de tela

O contrato do BFF espelha a navegação, não o modelo de domínio. `GET /telas/curva` devolve versão, vértices, procedência, modelo e estado da última execução em uma resposta.

*Alternativa considerada*: proxy genérico com composição no cliente. Rejeitada — devolveria ao front a responsabilidade de conhecer a topologia, que é justamente o que o BFF existe para absorver.

*Custo aceito*: o BFF muda quando a tela muda. É esperado — é a natureza de um BFF, e é o preço de o front não conhecer o backend.

### D2 — Agregação em paralelo, com degradação parcial

As chamadas às dependências saem em paralelo, com timeout individual. Se uma falha, a resposta traz as demais seções preenchidas e a que falhou explicitamente marcada como indisponível, com o motivo.

*Por que importa*: o motor cair não pode impedir o operador de ver os vértices já publicados e o estado da execução. Falhar a resposta inteira transformaria uma degradação em indisponibilidade.

### D3 — Autorização no BFF, com credencial de serviço para dentro

O BFF valida o token do usuário, resolve perfis e decide se a operação é permitida. Para dentro, chama as APIs com credencial de serviço, propagando a identidade do usuário como contexto para auditoria.

*Risco reconhecido*: as APIs internas confiam no BFF. A mitigação é de rede — elas não são alcançáveis de fora — e as próprias APIs mantêm suas verificações de perfil, de forma que o BFF é a primeira barreira, não a única.

### D4 — Três perfis, verificados por operação

`CURVE_VIEWER` consulta. `CURVE_OPERATOR` acrescenta disparo de ingestão e backfill. `CURVE_ADMIN` acrescenta cadastro de curva, importação de modelo Groovy e troca do modelo de uma curva. A verificação é por operação, não por endpoint agregador — uma tela pode ser visível a todos e conter uma ação restrita.

### D5 — O BFF não converte número

Valores de mercado chegam como texto numérico e saem como texto numérico. O BFF não faz aritmética com eles, não formata e não arredonda. Formatação é responsabilidade do front, que conhece a localidade.

### D6 — Disparo manual é uma ação de tela com retorno rastreável

O endpoint de disparo recebe a data e a lista do que consumir — conjuntos de dado individual e/ou o de curva pronta — e devolve o `correlacao_id` da execução, para que a tela acompanhe sem adivinhar. Se já houver execução em andamento, devolve a existente com seu progresso.

### D7 — Erro traduzido, não recriado

Erro de negócio vem das APIs de domínio com código e mensagem; o BFF traduz para o contrato de tela preservando o código original. Ele MUST NOT inventar validação própria de domínio, para não haver duas verdades sobre o que é válido.

### D8 — Sem cache de negócio no BFF

O cache que importa é o de interpolação, e ele vive no motor com invalidação por versão. Cachear no BFF criaria uma segunda camada com invalidação independente — e a chance de a tela mostrar curva antiga depois de uma republicação.

## Risks / Trade-offs

- **BFF vira gargalo de acoplamento entre front e back** → é o papel dele; mitigado por contrato OpenAPI próprio e por endpoints pequenos, um por tela, que mudam de forma isolada.
- **Agregação esconde de onde veio a lentidão** → cada seção da resposta carrega o tempo da sua origem, e o `correlacao_id` atravessa as chamadas internas.
- **Degradação parcial mal comunicada faz o usuário achar que o dado sumiu** → a seção indisponível é marcada explicitamente, com motivo, e o front deve exibir isso como estado, nunca como vazio.
- **APIs internas confiando no BFF** → mitigado por isolamento de rede e por as APIs manterem sua própria verificação de perfil.
- **Token expirando no meio de uma sessão longa de tela** → resposta de não autorizado padronizada, com o front reautenticando pelo shell Liquid.
- **Timeout agregado maior que a paciência do usuário** → timeout individual por dependência, sempre menor que o timeout total, para que a resposta chegue degradada em vez de estourar.

## Migration Plan

1. Esqueleto com OIDC, perfis e um endpoint de catálogo — prova a fronteira de segurança ponta a ponta.
2. Tela de curva agregada: versão, vértices, procedência, modelo e execução.
3. Interpolação e comparação.
4. Disparo manual de ingestão e monitoramento de execuções.
5. Cadastro de curva e gestão de modelos, restritos a administrador.
6. Degradação parcial e tratamento uniforme de erro.

**Rollback**: serviço sem estado; reverter é voltar a imagem. Nenhum dado de domínio é afetado.

## Open Questions

- O shell Liquid já entrega o token ao micro-frontend, ou o app Angular precisa iniciar o fluxo OIDC por conta própria? Muda a integração de autenticação no front.
- Os perfis vêm como claim no token do IdP corporativo, ou precisam ser resolvidos por consulta a um serviço de autorização?
- Deve haver limitação de taxa por usuário nas consultas de interpolação, dado que a tela pode pedir muitos prazos?
- Exportação de curva em CSV é responsabilidade do BFF ou do front, a partir do JSON já recebido?
- A sessão deve ser mantida por cookie de sessão do BFF ou por token no cabeçalho a cada chamada? Afeta CSRF e o desenho de logout.
