## ADDED Requirements

### Requirement: Ajuste da tela que já existe
A tela de tarefas que já existe (CRUD e execução) SHALL ser ajustada no lugar, mantendo os componentes, o estilo, os nomes e o comportamento que o outro desenvolvedor já fez; a implementação MUST NOT reescrever a tela nem criar uma segunda tela de tarefas. O que esta spec não cita fica como está.

#### Scenario: CRUD preservado
- **WHEN** a change é aplicada
- **THEN** criar, editar, excluir e listar tarefas funcionam como antes

### Requirement: Execução com data-base
Na execução de uma tarefa de download (`carga-download-site` e `carga-data-license`), a tela SHALL oferecer a data-base opcional (`dd/mm/aaaa`, com o texto "Vazio: data-base padrão da tarefa") e, só com data informada, a opção "Baixar de novo da fonte" (desmarcada). A tela SHALL chamar a execução com `dataBase` em `AAAA-MM-DD` (ou sem ela) e `incluirDownload`, sem `Authorization`, com tempo limite de pelo menos 130 segundos. Enquanto executa, o botão fica desabilitado.

#### Scenario: Sem data
- **WHEN** o operador executa a tarefa B3 sem data
- **THEN** a chamada vai sem `dataBase`, com `incluirDownload` = `false`

#### Scenario: Com data passada
- **WHEN** o operador informa `10/09/2026` e marca "Baixar de novo da fonte"
- **THEN** a chamada vai com `dataBase` = `2026-09-10` e `incluirDownload` = `true`

### Requirement: Resultado da execução
A resposta 200 traz `{ resultado, fonte, dataBase, incluirDownload, idCarga, statusHttp, detalhe, usuario, correlationId }`. A tela SHALL mostrar a situação (`SUCESSO` "Sucesso", `NAO_RECEBIDA` "Arquivo ainda não recebido", `NAO_IMPLEMENTADA` "Fonte ainda não implementada", `ERRO` "Erro"), a data-base usada em `dd/mm/aaaa`, o `idCarga` e o `detalhe`. Um 200 com resultado diferente de `SUCESSO` MUST aparecer como resultado, não como erro. O 409 (tarefa já em execução) e o 400 (tarefa desabilitada ou removida) SHALL aparecer na própria tela, com a mensagem do serviço.

#### Scenario: Arquivo ainda não recebido
- **WHEN** a resposta é 200 com `resultado` = `NAO_RECEBIDA` e `dataBase` = `2026-09-14`
- **THEN** a tela mostra "Arquivo ainda não recebido" e "Data-base: 14/09/2026", sem a página de erro

#### Scenario: Já em execução
- **WHEN** a resposta é 409
- **THEN** a tela mostra a mensagem do serviço e mantém a lista de tarefas

### Requirement: Alertas do dia
A tela SHALL mostrar os alertas de `GET /api/v1/alertas?dataInicial=&dataFinal=` (hoje nos dois, por padrão), do mais recente para o mais antigo: tarefa, tipo (`CARGA_NAO_RECEBIDA` "Carga não recebida"), data-base (`dd/mm/aaaa`), instante (horário de Brasília) e detalhe. Sem alertas, o texto "Nenhum alerta hoje". Enquanto a rota não existir (antes da v1), a falha da consulta MUST NOT atrapalhar o resto da tela.

#### Scenario: Alerta da ANBIMA
- **WHEN** existe o alerta `CARGA_NAO_RECEBIDA` da tarefa ANBIMA com data-base `2026-09-14`
- **THEN** a tela mostra "Carga não recebida", a tarefa ANBIMA e "14/09/2026"
