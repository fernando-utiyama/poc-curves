# Realm `curvas` (bootstrap local)

Importado automaticamente pelo Keycloak na subida (`start-dev --import-realm`,
ver `compose.yaml`). Sem isto, o realm `curvas` referenciado em
`SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_JWK_SET_URI` (curve-api, curve-bff)
nunca existia — nenhum token real podia ser emitido, e nenhuma integração de
autenticação/autorização podia ser verificada de ponta a ponta contra um IdP
real (encontrado na auditoria de 2026-08-22).

**Só para ambiente local — nunca usar estas credenciais em produção.**

| Usuário | Senha | Papel |
|---|---|---|
| `leitor` | `leitor123` | `CURVE_VIEWER` |
| `operador` | `operador123` | `CURVE_OPERATOR` |
| `administrador` | `administrador123` | `CURVE_ADMIN` |

Client público `curve-web-ui`, com `directAccessGrantsEnabled: true` — a
intenção era permitir obter um token real via `password` grant, útil para
testar `curve-api`/`curve-bff` diretamente com `curl`, sem precisar do fluxo
de navegador:

```bash
curl -s -X POST http://localhost:8180/realms/curvas/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=curve-web-ui \
  -d username=administrador \
  -d password=administrador123 \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['access_token'])"
```

**Problema conhecido, não resolvido nesta sessão**: esse comando retorna
`{"error":"invalid_grant","error_description":"Account is not fully set up"}`
para QUALQUER usuário do realm `curvas` (inclusive um criado do zero via API
de admin, fora do import — isolado e confirmado). O mesmo grant `password`
funciona normalmente contra o realm `master` (é como o token de admin usado
neste README foi obtido). As comparações de configuração entre os dois
realms (`requiredCredentials`, `requiredActions`, `otpPolicy`,
`directGrantFlow`) não mostraram nenhuma diferença — a causa raiz não foi
encontrada. O endpoint de autorização via navegador
(`/protocol/openid-connect/auth`, o fluxo que o `curve-web-ui` de fato usa)
responde 200 normalmente, então o problema parece specific ao grant
`password`/direct access — mas isso não foi confirmado ponta a ponta com um
navegador real. Tratar como bloqueado até investigar mais: o realm, os
papéis e os usuários existem e estão corretos (verificado via API de admin),
mas emitir um token de verdade para testar `curve-api`/`curve-bff` contra um
IdP real ainda não foi possível nesta sessão.
