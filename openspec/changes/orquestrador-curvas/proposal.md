## Why

O orquestrador (`services/orchestrator`) é quem dispara todo o processo das curvas, mas nenhuma change diz o que ele precisa fazer. O conector (change `conector-b3-webhook-ingest`, D18) deixa para o orquestrador o horário do download da B3, as novas tentativas quando o arquivo ainda não saiu e o alerta de carga não recebida. O engine (change `engine-modelos-curva`, D37) ganhou a rota `POST /api/v1/construcoes/{dataBase}`, que o orquestrador chama para construir as curvas derivadas e cobrir um aviso de carga perdido. Sem esta change, esses três pontos ficam sem dono.

O código real do orquestrador ainda vai ser transcrito das fotos; o que está hoje em `services/orchestrator` não é a referência. Esta change fixa o **comportamento** esperado, para que a implementação se encaixe na estrutura real quando ela chegar.

## What Changes

- **Tarefas das curvas no orquestrador:**
  - `B3_TAXA_SWAP_DOWNLOAD`: chama o download do `TaxaSwap` no conector para a data-base do dia, repete até o arquivo da data sair ou até o horário limite, e alerta `CARGA_NAO_RECEBIDA` se não sair;
  - `CONSTRUCAO_CURVAS_DATA`: chama a construção automática da data no engine nos horários configurados e alerta `CURVAS_PENDENTES` se, no último horário do dia, ainda houver curva com erro, sem insumo ou sem pontos.
- **Data-base e dia útil:** a data-base é o dia de hoje no horário de Brasília; dia não útil no `Brazil`/`Settlement` do engine não dispara nada.
- **Conferência da data do arquivo:** o download da B3 pode devolver um arquivo de dia anterior (busca de dias anteriores do conector); o orquestrador só considera a carga do dia recebida quando a `dataBase` da resposta é a data pedida.
- **Execução manual:** um operador pode executar qualquer tarefa para uma data escolhida, inclusive passada, e forçar o reprocessamento B3 de uma data.
- **Identidade de serviço:** o orquestrador chama o conector e o engine com token do Entra ID (client credentials), com os papéis `Curvas.Orquestrador` e `Curvas.Leitura` no engine e a identidade aceita pelo conector.
- **Execução única, logs e métricas:** cada disparo agendado roda uma vez, mesmo com várias instâncias; toda chamada leva `X-Correlation-Id`; eventos e métricas para alerta.
- **Sem infraestrutura nova:** nenhum tópico, fila ou tabela. As tarefas das outras fontes (ANBIMA, Bloomberg) entram quando os conectores delas existirem.

## Capabilities

### New Capabilities
- `orquestracao-curvas`: tarefas agendadas das curvas (download B3 e construção da data), data-base e dia útil, novas tentativas, alertas, execução manual, identidade de serviço, execução única, logs e métricas.

### Modified Capabilities
<!-- Nenhuma: não há specs arquivadas na develop. -->

## Impact

- **services/orchestrator:** tarefas novas e o cliente HTTP do conector e do engine. A forma de registrar tarefas, agendar e garantir execução única segue a estrutura do orquestrador real, a transcrever.
- **Entra ID:** identidade de serviço do orquestrador com `Curvas.Orquestrador` e `Curvas.Leitura` no registro do engine; `appid` dela em `B3_ORQUESTRADOR_APP_ID` no conector.
- **Dependências:** rotas `b3/taxa-swap/download` e `b3/taxa-swap/reprocessamento` do conector (change `conector-b3-webhook-ingest`); `POST /api/v1/construcoes/{dataBase}` e a exportação de calendário do engine (change `engine-modelos-curva`).
- **Fora de escopo:** download ANBIMA e Bloomberg (conectores sem change ainda), painel (é do `services/curves`), qualquer lógica de curva.
