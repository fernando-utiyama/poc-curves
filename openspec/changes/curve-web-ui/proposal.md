## Why

Toda a plataforma existe para responder perguntas que hoje só têm resposta em planilha e conversa: *a curva de hoje saiu?*, *por que este vértice mudou?*, *como a curva que a gente construiu difere da que a B3 divulgou?*. Sem tela, a POC prova o fluxo técnico e não prova o produto.

Falta também o caminho operacional que o pedido original destaca: **um lugar onde o operador dispare o consumo dos dados da B3** — tanto os arquivos BVBG quanto o endpoint da curva pronta — e acompanhe o que aconteceu, sem depender de alguém rodar comando.

Esta mudança especifica o front Angular que fecha o ciclo da plataforma até o usuário. Na POC ele é uma **aplicação Angular autônoma**, sem shell e sem Module Federation — o embarque no shell Liquid continua sendo o desenho-alvo, e o app é estruturado para permiti-lo depois sem reescrita, mas integrar com o host não faz parte deste escopo.

## What Changes

- **App Angular autônomo**: servido estaticamente, com autenticação OIDC própria contra o Keycloak local, e configuração (URL do BFF, identidade) injetada em tempo de execução.
- **Preparado para embarque futuro**: rota base configurável, nenhuma suposição sobre a rota raiz do navegador, tema por tokens substituíveis e contexto de usuário obtido de uma fonte única e trocável — o suficiente para virar micro-frontend depois sem reescrever telas.
- **Tela de catálogo**: lista as curvas com modo de origem (construída ou importada), estado, versão vigente e modelo apontado.
- **Tela de cadastro/edição de curva**: convenções, contagem de dias, calendário, interpolador, política de extrapolação, política de arredondamento, vínculos de fonte, dependências e **escolha do modelo de construção**. Deixa explícito que salvar cria uma versão nova da definição.
- **Viewer de curva**: gráfico e tabela de vértices, seletor de data, de momento e de versão — incluindo consulta por instante —, com a procedência ao lado do número e o modelo que a produziu.
- **Consulta interpolada**: prazo arbitrário ou lista de prazos, com sinalização de ponto extrapolado e erro claro quando a política é estrita.
- **Tela de disparo manual de ingestão**: escolhe a data e o que consumir — arquivos BVBG e demais dados individuais, e/ou o endpoint da curva pronta —, avisa quando a data não é dia de pregão, e entrega o `correlacao_id` para acompanhamento. Inclui backfill de janela de datas.
- **Tela de carga manual de curva**: quando a curva não sai antes do corte, o operador sobe um CSV ou planilha, com **modelo disponível para download na própria tela**, já com o cabeçalho e os prazos daquela curva. Justificativa é obrigatória, os erros voltam por linha, e a curva carregada aparece permanentemente marcada como tal.
- **Painel do dia como tela inicial**: uma linha por curva, com estado, horário limite e tempo restante ou margem. Responde "a curva de hoje saiu?" de relance, que é a pergunta que a mesa faz todo dia. Curva em risco aparece **antes** de falhar.
- **Redisparo direto da tela**: quando uma ingestão trava ou falha, o operador redispara ali mesmo; a nova tentativa entra na faixa prioritária e não fica atrás da travada.
- **Monitor de execuções**: lista com estado, etapa, duração, `correlacao_id` e causa da falha; ausência de dado aparece como estado próprio, não como erro.
- **Alerta global de pendência de dead-letter**: um indicador presente em todas as telas mostra quantos grupos de falha estão abertos e há quanto tempo. Clicar leva à tela de pendências, onde o operador vê a causa, reprocessa ou descarta com justificativa. **O alerta desaparece sozinho** quando o reprocessamento conclui com sucesso — ninguém precisa dar baixa manualmente.
- **Tela de modelos e comparação**: importa modelo Groovy, aponta a curva para outro modelo, e compara construída contra importada ou modelo contra modelo, prazo a prazo.
- **Precisão na borda**: valores chegam como texto numérico e são convertidos apenas para exibição — nunca para cálculo no navegador.
- **Ações visíveis conforme perfil**: o que o usuário não pode fazer aparece desabilitado com o motivo, em vez de simplesmente sumir ou falhar no clique.

## Capabilities

### New Capabilities

- `curve-ui-shell-integration`: aplicação Angular autônoma da POC, autenticação OIDC própria, tratamento de sessão expirada, fronteira de comunicação restrita ao BFF, cliente gerado do contrato, empacotamento com configuração em tempo de execução, e as garantias estruturais que permitem embarcar no shell depois.
- `curve-management-screens`: as telas de produto — catálogo, cadastro e edição de curva, viewer com vértices e procedência, consulta interpolada, disparo manual de ingestão para os dois tipos de insumo, backfill, monitor de execuções, alerta global e tela de pendências de dead-letter, e modelos com comparação —, incluindo estados de carregamento, erro, degradação parcial e permissão.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo projeto**: `web/curve-web-ui/` em Angular 18+, TypeScript estrito, aplicação autônoma.
- **Depende exclusivamente de** `curve-bff` — o front não conhece nem alcança as APIs de domínio.
- **Depende do contrato** `contracts/openapi/curve-bff.yaml`, do qual os tipos do cliente são gerados.
- **Não integra** com o shell Liquid nesta mudança; o contrato do host permanece em aberto e será tratado quando o embarque entrar em escopo.
- **Precisão**: valores de mercado chegam como texto; conversão para número só na formatação de exibição, jamais para aritmética.
- **Infra local**: roda sob Podman no compose, servido estaticamente, com o BFF e o Keycloak locais.
- **Fora de escopo**: qualquer regra de domínio, cálculo de curva ou acesso direto a dado; e o próprio shell Liquid, que é sistema de terceiros.
