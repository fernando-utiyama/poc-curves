## Purpose

Define a API HTTP do engine para construir, consultar, interpolar e simular curvas, identificando a curva pelo código (ou, só para leitura, pelo nome) e pela data-base, e para gerir os scripts Groovy de modelo por tipo e nome. Fixa rotas, parâmetros, corpos, códigos HTTP e códigos de erro.

## ADDED Requirements

### Requirement: Rotas de operação e extensões
O engine SHALL expor, além das rotas da change `engine-construcao-curvas`, estas rotas (prefixo `/api/v1`):

| Método e rota | Uso | Papel exigido |
|---|---|---|
| `GET /curvas/{codigo}/{dataBase}/simulacao?du=&data=&formato=&fonte=&produto=` | simular construção sem gravar, pela origem principal ou por uma secundária | `Curvas.Leitura` |
| `GET /curvas/{codigo}/{dataBase}/auditoria?formato=` | arquivo de auditoria montado na hora (spec `curve-audit-history`) | `Curvas.Leitura` |
| `GET /curvas/por-nome/{dataBase}/simulacao?nome=&du=&data=&formato=` | simular, pelo nome | `Curvas.Leitura` |
| `POST /modelos/{tipo}/{nome}` | enviar script | `Curvas.ModelosAutor` |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/validacao` | validar script | `Curvas.ModelosAutor` |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/ativacao` | ativar script | `Curvas.ModelosAprovador` |
| `POST /modelos/{tipo}/{nome}/desativacao` | desativar script | `Curvas.ModelosAprovador` |
| `GET /modelos/{tipo}/{nome}` | listar versões | `Curvas.Leitura` |
| `POST /calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=` | importar planilha de feriados (spec `calendar-management`) | `Curvas.ModelosAutor` |

#### Scenario: Simulação disponível pelo código e pelo nome
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/simulacao` e `GET /api/v1/curvas/por-nome/2026-09-14/simulacao?nome=DIxPRE`
- **THEN** as duas respostas trazem a mesma simulação, sem gravar nada

### Requirement: Autenticação e papéis
Toda rota de `/api/v1` MUST exigir um token JWT do Microsoft Entra ID (`Authorization: Bearer`), validado por emissor, audiência, assinatura e validade (propriedades `engine.seguranca.emissor` e `engine.seguranca.audiencia`). Só os endpoints de saúde do Actuator ficam sem autenticação. O acesso SHALL ser decidido pelos papéis de aplicação (`roles`) do token, conforme a tabela de rotas:
- `Curvas.Leitura`: consultas, interpolação, simulação, auditoria, situação, valores aceitos, catálogo e lista de scripts;
- `Curvas.Operador`: construir e recalcular; inclui `Curvas.Leitura`;
- `Curvas.Processor`: webhook de carga; concedido só à identidade de serviço do processor (client credentials);
- `Curvas.Orquestrador`: construção automática da data; concedido só à identidade de serviço do orquestrador (client credentials);
- `Curvas.ModelosAutor`: enviar e validar scripts;
- `Curvas.ModelosAprovador`: ativar e desativar scripts.

Token ausente ou inválido MUST resultar em 401 `NAO_AUTENTICADO`; token sem o papel exigido, em 403 `SEM_PERMISSAO`. O usuário gravado na auditoria e no log SHALL ser o `preferred_username` do token ou, para identidade de serviço, o `appid`. A validação do token SHALL ser a do Resource Server do Spring (`spring.security.oauth2.resourceserver.jwt`), e MUST falhar fechada em todo ambiente implantado: sem emissor ou audiência configurados, a aplicação MUST NOT subir; com o Entra ID indisponível para obter as chaves, as requisições MUST ser recusadas com 401 até as chaves serem obtidas. Nenhum validador alternativo que aceite token sem verificar assinatura ("mock") SHALL existir no código de produção, qualquer que seja o nome do perfil. O único modo sem Entra ID é o teste automatizado, com o suporte de teste do Spring Security.

#### Scenario: Emissor não configurado
- **WHEN** o engine sobe em qualquer perfil sem `engine.seguranca.emissor`
- **THEN** a aplicação não sobe, e o log diz a propriedade que falta

#### Scenario: Entra ID indisponível na subida
- **WHEN** o engine sobe e o Entra ID não responde para entregar as chaves
- **THEN** toda rota protegida responde 401 até as chaves serem obtidas; nenhum token é aceito sem validação

#### Scenario: Leitura sem papel
- **WHEN** um usuário autenticado sem `Curvas.Leitura` consulta `GET /api/v1/curvas/PRE/2026-09-14`
- **THEN** a resposta é 403 com `SEM_PERMISSAO`

#### Scenario: Webhook por usuário comum
- **WHEN** um usuário com `Curvas.Operador` chama `POST /api/v1/cargas`
- **THEN** a resposta é 403 com `SEM_PERMISSAO`, porque o webhook exige `Curvas.Processor`

### Requirement: Saída em planilha nas rotas de leitura
Com `formato=xlsx`, as rotas de consulta de pontos, de interpolação e de simulação SHALL responder com a planilha de memória de cálculo definida na spec `curve-calculation-memory`, em vez do JSON, com `Content-Type` `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` e `Content-Disposition: attachment; filename="{codigo}_{dataBase}_{FONTE}_{AAAAMMDDHHmmss}.xlsx"`, onde `FONTE` é `GRAVADA` ou `SIMULACAO` e o carimbo está no horário de Brasília. Erros continuam respondendo em JSON.

#### Scenario: Download da consulta
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14?formato=xlsx`
- **THEN** a resposta é um arquivo `PRE_2026-09-14_GRAVADA_<horário>.xlsx` com a memória de cálculo dos pontos gravados

### Requirement: Gestão de scripts de modelo
`{tipo}` SHALL ser `construcao`, `interpolacao` ou `calendario`. O envio SHALL receber o código do script como texto no corpo (`text/plain`) e criar uma versão em `RASCUNHO`. As respostas SHALL trazer tipo, nome, versão, status e hash. As mensagens de falha de validação SHALL trazer o motivo e a linha do script quando houver, sem stack trace.

#### Scenario: Ativação de versão não validada
- **WHEN** o cliente pede a ativação de uma versão que não passou na validação
- **THEN** a resposta é 422 com `SCRIPT_INVALIDO`, informando que a versão precisa ser validada antes
