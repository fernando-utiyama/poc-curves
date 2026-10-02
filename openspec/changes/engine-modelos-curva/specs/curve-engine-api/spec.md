## Purpose

Define a API HTTP do engine para construir, consultar, interpolar e simular curvas, identificando a curva pelo código (ou, só para leitura, pelo nome) e pela data-base, e para gerir os scripts Groovy de modelo por tipo e nome. Fixa rotas, parâmetros, corpos, códigos HTTP e códigos de erro.

## ADDED Requirements

### Requirement: Rotas de operação e extensões
O engine SHALL expor, além das rotas da change `engine-construcao-curvas`, estas rotas (prefixo `/api/v1`):

| Método e rota | Uso |
|---|---|
| `GET /curvas/{codigo}/{dataBase}/simulacao?du=&data=&formato=&fonte=&produto=` | simular construção sem gravar, pela origem principal ou por uma secundária |
| `GET /curvas/{codigo}/{dataBase}/auditoria?formato=` | arquivo de auditoria montado na hora (spec `curve-audit-history`) |
| `GET /curvas/por-nome/{dataBase}/simulacao?nome=&du=&data=&formato=` | simular, pelo nome |
| `POST /modelos/{tipo}/{nome}` | enviar script |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/validacao` | validar script |
| `POST /modelos/{tipo}/{nome}/versoes/{versao}/ativacao` | ativar script |
| `POST /modelos/{tipo}/{nome}/desativacao` | desativar script |
| `GET /modelos/{tipo}/{nome}` | listar versões |
| `POST /calendarios/{nome}/importacao?mercado=&anoInicial=&anoFinal=` | importar planilha de feriados (spec `calendar-management`) |

#### Scenario: Simulação disponível pelo código e pelo nome
- **WHEN** o cliente chama `GET /api/v1/curvas/PRE/2026-09-14/simulacao` e `GET /api/v1/curvas/por-nome/2026-09-14/simulacao?nome=DIxPRE`
- **THEN** as duas respostas trazem a mesma simulação, sem gravar nada

### Requirement: Usuário nas rotas de script
Valem a spec `curve-engine-api` da change `engine-construcao-curvas` (o engine não autentica; `acionadoPor` vem da rota; o `usuario` vem do cabeçalho opcional `X-Usuario`). Como o `estado.json` grava `autor` e `aprovador`, o envio de script (`POST /modelos/{tipo}/{nome}`) e a ativação (`POST /modelos/{tipo}/{nome}/versoes/{versao}/ativacao`) MUST exigir o `X-Usuario`, e sem ele a resposta é 400 `PARAMETRO_INVALIDO`. O engine confia no valor, e a separação entre autor e aprovador não existe: quem ativa pode ser o próprio autor.

#### Scenario: Envio sem usuário
- **WHEN** o cliente chama `POST /api/v1/modelos/construcao/MeuModelo` sem o cabeçalho `X-Usuario`
- **THEN** a resposta é 400 com `PARAMETRO_INVALIDO`, e nenhuma versão é criada

#### Scenario: Ativação com usuário
- **WHEN** o cliente ativa uma versão `VALIDADA` com `X-Usuario: maria`
- **THEN** o `estado.json` grava `aprovador` = `maria`, e a ativação segue mesmo que `maria` seja o autor

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
