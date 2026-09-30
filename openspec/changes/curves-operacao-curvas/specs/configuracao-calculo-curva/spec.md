## Purpose

Modelo de construção por origem secundária na configuração de cálculo. A configuração em si está na change `curves-cadastro-curvas`.

## ADDED Requirements

### Requirement: Modelo de construção por origem secundária
`parametros` MAY ter `MODELOS_POR_ORIGEM`: um objeto em que cada chave é `{provedor}/{produto}` de uma ligação da curva (spec `ligacao-curva-provedor`) e o valor é o modelo de construção que lê aquela origem quando o usuário constrói a curva por ela no engine (spec `curve-build-pipeline` do change `engine-modelos-curva`, requisito "Construção por uma origem secundária"). O serviço SHALL recusar com 422 `DADOS_INVALIDOS` a chave fora do formato `{provedor}/{produto}` e o valor que não seja texto de 1 a 100 caracteres. Sem bloquear, SHALL trazer os avisos:
- `MODELO_POR_ORIGEM_SEM_LIGACAO`: a chave não corresponde a nenhuma ligação atual da curva, ou corresponde à origem principal (a entrada é ignorada pelo engine);
- `ORIGEM_INCOMPATIVEL_COM_MODELO`: o modelo nativo informado não aceita aquela origem;
- `MODELO_NAO_NATIVO`: o modelo informado não é nativo.

A ausência de `MODELOS_POR_ORIGEM` não é aviso: sem ela, o engine usa `modeloConstrucao` também para a origem secundária, se ele aceitar a origem.

#### Scenario: Reserva da B3 para uma curva ANBIMA
- **WHEN** a curva `DI_BACKUP` tem as ligações `ANBIMA`/`CZ` (prioridade 1) e `B3`/`TS` (prioridade 2), e a versão nova traz `MODELOS_POR_ORIGEM` = `{"B3/TS":"PRONTA_TS_B3"}`
- **THEN** a versão é criada sem aviso, e `cModDado` guarda o objeto no JSON compacto

#### Scenario: Ligação excluída depois
- **WHEN** a ligação `B3`/`TS` da `DI_BACKUP` é excluída, e a versão vigente ainda tem a chave `B3/TS`
- **THEN** a exclusão é feita com o aviso `MODELO_POR_ORIGEM_SEM_LIGACAO`, e a construção da `DI_BACKUP` pela origem principal continua funcionando

