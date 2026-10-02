## Purpose

Modelo de construção por origem secundária e consulta ao engine para os valores aceitos, na configuração de cálculo. A configuração em si está na change `curves-cadastro-curvas`.

## ADDED Requirements

### Requirement: Modelo de construção por origem secundária
`parametros` MAY ter `MODELOS_POR_ORIGEM`: um objeto em que cada chave é `{provedor}/{produto}` de um provedor da curva (spec `provedor-curva`) e o valor é o modelo de construção que lê aquela origem quando o usuário constrói a curva por ela no engine (spec `curve-build-pipeline` do change `engine-modelos-curva`, requisito "Construção por uma origem secundária"). O serviço SHALL recusar com 422 `DADOS_INVALIDOS` a chave fora do formato `{provedor}/{produto}` e o valor que não seja texto de 1 a 100 caracteres. Sem bloquear, SHALL trazer os avisos:
- `MODELO_POR_ORIGEM_SEM_PROVEDOR`: a chave não corresponde a nenhum provedor atual da curva, ou corresponde à origem principal (a entrada é ignorada pelo engine);
- `ORIGEM_INCOMPATIVEL_COM_MODELO`: o modelo nativo informado não aceita aquela origem;
- `MODELO_NAO_NATIVO`: o modelo informado não é nativo.

A ausência de `MODELOS_POR_ORIGEM` não é aviso: sem ela, o engine usa `modeloConstrucao` também para a origem secundária, se ele aceitar a origem.

#### Scenario: Reserva da B3 para uma curva ANBIMA
- **WHEN** a curva `DI_BACKUP` tem os provedores da curva `ANBIMA`/`CZ` (prioridade 1) e `B3`/`TS` (prioridade 2), e a versão nova traz `MODELOS_POR_ORIGEM` = `{"B3/TS":"PRONTA_TS_B3"}`
- **THEN** a versão é criada sem aviso, e `cModDado` guarda o objeto no JSON compacto

#### Scenario: Provedor excluído depois
- **WHEN** o provedor da curva `B3`/`TS` da `DI_BACKUP` é excluído, e a versão vigente ainda tem a chave `B3/TS`
- **THEN** a exclusão é feita com o aviso `MODELO_POR_ORIGEM_SEM_PROVEDOR`, e a construção da `DI_BACKUP` pela origem principal continua funcionando

### Requirement: Valores aceitos consultados ao engine
A partir desta change, o serviço SHALL consultar o engine (`GET /api/v1/valores-cadastro`, spec `curve-engine-api` do change `engine-construcao-curvas`) em `GET /api/v1/curvas-mercado/valores`, com tempo limite de 10 segundos e cache da resposta por 5 minutos, para acrescentar à tabela embutida os modelos de construção, interpoladores e calendários dos scripts Groovy ativos. O engine não exige autenticação, então a chamada MUST NOT levar `Authorization`. O serviço MUST NOT falhar por causa do engine: se ele não responder, SHALL devolver a tabela embutida, só com os modelos nativos, e o aviso `VALORES_SEM_ENGINE`.

Um teste de contrato SHALL comparar a tabela embutida no serviço com a parte fixa de `GET /api/v1/valores-cadastro` do engine: qualquer diferença falha o build.

#### Scenario: Scripts Groovy no formulário
- **WHEN** o engine tem um script Groovy ativo de interpolação e o front abre o formulário de nova versão da `PRE`
- **THEN** a lista de interpoladores traz os nativos e o script Groovy ativo

#### Scenario: Engine fora
- **WHEN** o engine não responde e o front pede os valores
- **THEN** a resposta é 200 com os valores fixos e os modelos nativos, e o aviso `VALORES_SEM_ENGINE`
