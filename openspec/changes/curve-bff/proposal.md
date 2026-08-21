## Why

O front precisa de uma tela de curva que mostre, ao mesmo tempo, os vértices, a versão vigente, a procedência, o estado da última execução e qual modelo produziu aquilo. Nenhuma API de domínio entrega isso em uma chamada — nem deveria, porque cada uma tem uma responsabilidade estreita. Sem um BFF, o Angular acaba orquestrando cinco chamadas, montando a tela no navegador e conhecendo a topologia interna da plataforma.

Há também um problema de segurança: expor `curve-api`, `curve-engine` e `curve-orchestrator` ao navegador significa três superfícies públicas com regras de autorização replicadas. Esta mudança estabelece o **BFF como única fronteira exposta**, dono da autenticação, da autorização por perfil e dos contratos orientados a tela.

## What Changes

- **Fronteira única**: o navegador fala apenas com o BFF. `curve-api`, `curve-engine` e `curve-orchestrator` não são expostos, e o BFF os chama com credencial de serviço.
- **Autenticação OIDC**: valida o token do usuário, resolve identidade e perfis, e rejeita requisição sem token válido.
- **Autorização por perfil**: `CURVE_VIEWER` consulta; `CURVE_OPERATOR` também dispara ingestão e backfill; `CURVE_ADMIN` também cadastra curva, importa modelo Groovy e troca o modelo de uma curva.
- **Contratos orientados a tela**: cada endpoint corresponde a uma tela ou a uma ação de tela, agregando em uma resposta o que hoje exigiria várias chamadas — por exemplo, a tela de curva devolve versão, vértices, procedência, modelo e estado da última execução de uma vez.
- **Endpoint de disparo manual de ingestão**, cobrindo os dois tipos de insumo — arquivos BVBG e demais dados individuais, e o endpoint de curva pronta da B3 — com retorno do `correlacao_id` para acompanhamento.
- **Painel do dia**: um recurso que responde, para todas as curvas de uma data, "saiu ou não saiu, e quanto falta para o corte". É a tela que a mesa abre de manhã e deixa aberta.
- **Redisparo prioritário**: a ação de disparo manual informa ao orquestrador a faixa prioritária, e a resposta traz o `correlacao_id` da nova tentativa — inclusive quando a anterior está travada.
- **Agregação de comparação**: construída contra importada, ou modelo contra modelo, em um contrato pronto para a tela de comparação.
- **Degradação parcial**: quando uma dependência está fora do ar, a resposta entrega o que conseguiu e sinaliza a parte indisponível, em vez de falhar inteira.
- **Precisão preservada**: valores de mercado trafegam como texto numérico até o navegador.
- **Paginação, filtro e ordenação** resolvidos no BFF, com contrato uniforme entre telas.

## Capabilities

### New Capabilities

- `bff-security`: autenticação OIDC, resolução de perfis, autorização por operação, credencial de serviço para as chamadas internas, propagação de `correlacao_id` e regra de não exposição das APIs de domínio.
- `curve-bff-aggregation`: contratos orientados a tela — catálogo, curva, interpolação, comparação, execuções e modelos —, agregação de múltiplas fontes em uma resposta, disparo manual de ingestão para os dois tipos de insumo, degradação parcial, paginação e preservação de precisão.

### Modified Capabilities

<!-- Nenhuma. -->

## Impact

- **Novo serviço**: `services/curve-bff/` em Java 21 / Spring Boot 3.4.x, sem Lombok.
- **Depende de** `curve-api`, `curve-engine` e `curve-orchestrator` pelos contratos OpenAPI versionados em `contracts/openapi`.
- **Não acessa** banco de dados nem Kafka — nenhuma exceção.
- **Consumido por** `curve-web-ui`, com contrato próprio em `contracts/openapi/curve-bff.yaml`.
- **Superfície pública**: é o único serviço alcançável pelo navegador; CORS, limites de payload e limitação de taxa se aplicam aqui.
- **Infra local**: exige o Keycloak do compose Podman para validar token no ambiente de desenvolvimento.
- **Fora de escopo**: qualquer cálculo, persistência de domínio, agendamento ou ingestão.
