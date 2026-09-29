## Context

- O orquestrador real ainda não foi transcrito; `services/orchestrator` hoje não é referência. Esta change descreve o que ele faz nas curvas, sem supor a sua estrutura interna (agendador, cadastro de tarefas, persistência).
- **Conector B3** (change `conector-b3-webhook-ingest`): `POST /api/b3/taxa-swap/download?date=AAAA-MM-DD` baixa o `.ex_` da data (com busca de dias anteriores, débito registrado), grava, arquiva e publica; responde 200 com `idCarga`, `dataBase`, `hashArquivo` e `origem`, 502 se a B3 falhar, 503 com Blob ou Kafka fora. `POST /api/b3/taxa-swap/reprocessamento?dataBase=` reprocessa o arquivo já gravado. Carga repetida gera o mesmo `idCarga`.
- **Processor:** grava a carga e avisa o engine por webhook; o orquestrador não participa desse trecho.
- **Engine** (change `engine-modelos-curva`): `POST /api/v1/construcoes/{dataBase}` (papel `Curvas.Orquestrador`) constrói com as regras automáticas todas as curvas da data que têm insumo e não têm pontos, e devolve a situação de cada curva; nunca recalcula. `GET /api/v1/calendarios/Brazil?mercado=Settlement&anoInicial=&anoFinal=` (papel `Curvas.Leitura`) exporta os feriados.

## Goals / Non-Goals

**Goals:** disparar o download B3 e a construção da data nos horários certos; perceber quando o arquivo do dia ainda não saiu; alertar o que não aconteceu; permitir disparo manual por data.

**Non-Goals:** lógica de curva, painel, downloads de ANBIMA e Bloomberg, estado próprio em tabela nova.

## Decisions

### D1. Duas tarefas, cada uma com uma responsabilidade
`B3_TAXA_SWAP_DOWNLOAD` garante que o arquivo do dia entrou; `CONSTRUCAO_CURVAS_DATA` garante que as curvas da data foram construídas. O orquestrador não espera o processor nem o webhook: a construção da data roda nos horários configurados e é idempotente (nunca recalcula), então rodar antes da carga só devolve `SEM_INSUMO`, e rodar de novo não estraga nada. **Alternativa rejeitada:** encadear a construção no fim do download, que exigiria saber quando o processor terminou.

### D2. Dia útil pelo calendário do engine
O calendário `Brazil`/`Settlement` é mantido no engine (inclusive feriado decretado por planilha). O orquestrador lê os feriados do ano pela exportação de calendário e guarda em memória até o fim do dia. Com o engine fora, só sábado e domingo são pulados, como no painel do `services/curves`, com aviso no log: disparar num feriado é inofensivo (o download falha ou traz um dia anterior; a construção devolve `SEM_INSUMO`). **Alternativa rejeitada:** lista de feriados própria no orquestrador, que divergiria do engine.

### D3. Arquivo de dia anterior não é carga do dia
O download do conector busca até 7 dias úteis anteriores quando o arquivo da data não existe. Antes da publicação da B3, isso devolve o arquivo de ontem com sucesso. O orquestrador compara a `dataBase` da resposta com a pedida: diferente é "ainda não publicado", e a tarefa tenta de novo. A carga de ontem republicada é inofensiva (mesmo `idCarga`; o engine responde `EXISTENTE`).

### D4. Horários sem valor padrão
Os horários de publicação dependem das fontes e da operação, e não foram informados. Cada tarefa tem os seus horários em configuração, em horário de Brasília, sem padrão: tarefa sem horário configurado não é agendada e gera erro no log na subida. A execução manual funciona mesmo assim.

### D5. Execução única e idempotência
O orquestrador roda em no mínimo duas instâncias no Azure, e cada disparo agendado precisa rodar uma vez. O mecanismo é o do orquestrador real (a confirmar na transcrição). Se ele falhar e duas instâncias dispararem, o efeito é nulo: o conector gera o mesmo `idCarga` e o engine não reconstrói o que já tem pontos.

### D6. Nenhum estado novo
Não há tabela, tópico ou arquivo novo. O histórico das execuções fica no log estruturado e nas métricas; se o orquestrador real já guardar execuções de tarefa, as tarefas das curvas usam esse mesmo registro.

### D7. Mesma base de implementação dos outros serviços
Quando o código real for transcrito, as tarefas das curvas seguem a base do engine (guia do `engine-modelos-curva`, seção 0): arquitetura hexagonal (as chamadas ao conector e ao engine atrás de portas de saída), Java 21 nativo (records para pedidos, respostas e eventos; `java.time`; `Thread.sleep(Duration)` nas esperas entre tentativas), virtual threads (`spring.threads.virtual.enabled`), sem `synchronized`, e o fuso da JVM fixado em `America/Sao_Paulo` no `main`, sem classe de relógio própria. Se o orquestrador real já tiver outro padrão, prevalece o dele.

## Risks / Trade-offs

- **Horários não informados.** → Sem padrão; a tarefa só é agendada quando configurada (D4). Definir com a operação antes do deploy.
- **Busca de dias anteriores do conector mascara atraso.** → Conferência da `dataBase` da resposta (D3); o débito da busca continua registrado no conector.
- **Engine fora no horário da construção.** → A tarefa repete dentro da janela; o próximo horário do dia e o alerta `CURVAS_PENDENTES` cobrem o resto.
- **Mecanismo de execução única desconhecido até a transcrição.** → Idempotência de conector e engine torna a duplicidade inofensiva (D5).

## Migration Plan

1. Identidade de serviço do orquestrador no Entra ID com `Curvas.Orquestrador` e `Curvas.Leitura` no engine; `appid` em `B3_ORQUESTRADOR_APP_ID` no conector.
2. Configurar os horários das tarefas.
3. Deploy depois do conector, do processor e do engine com as rotas usadas.
4. Rollback: desligar as tarefas; conector e engine continuam atendendo a execução manual pelo front.

## Open Questions

- Horários de cada tarefa, o horário limite do download B3 e o intervalo entre tentativas.
- Como o orquestrador real agenda, registra tarefas e garante execução única (depende da transcrição).
- Canal de alerta além do log e das métricas (e-mail, Teams), se houver.
