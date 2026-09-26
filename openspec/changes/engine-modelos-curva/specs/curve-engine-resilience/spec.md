## Purpose

Define como o engine se comporta diante de lentidão e falha das suas dependências (SQL Server, Blob Storage, scripts Groovy): tempo limite de cada chamada, quando repetir, como degradar, como sinalizar saúde e o que registrar em log para diagnosticar em produção.

## ADDED Requirements

### Requirement: Tempo limite em toda chamada externa
Toda chamada a dependência SHALL ter tempo limite configurável, com estes padrões:

| Dependência e operação | Propriedade | Padrão |
|---|---|---|
| SQL Server: obter conexão do pool | `engine.timeout.banco-conexao-ms` | 5.000 |
| SQL Server: comando (consulta ou gravação) | `engine.timeout.banco-comando-segundos` | 30 |
| SQL Server: trava da curva (construção e edição) | `engine.timeout.trava-curva-segundos` | 30 |
| Blob Storage: cada operação | `engine.timeout.blob-ms` | 5.000 |
| Script Groovy: cada chamada | `engine.groovy.timeout-segundos` | 5 |
| Requisição HTTP inteira (exceto webhook de carga) | `engine.timeout.requisicao-segundos` | 60 |
| Webhook de carga, requisição inteira | `engine.timeout.carga-segundos` | 120 |

Estourar um tempo limite MUST encerrar a operação com erro explícito: `CONSTRUCAO_EM_ANDAMENTO` para a trava, `MODELO_FALHOU` para o Groovy e `ERRO_INTERNO` para banco e requisição. Tempo esgotado no Blob segue a regra de Blob fora (nunca bloqueia construção nem consulta). Uma transação encerrada por tempo limite MUST ser desfeita por inteiro.

#### Scenario: Banco lento na construção
- **WHEN** a gravação dos pontos passa de 30 segundos
- **THEN** a transação é desfeita, nenhum ponto nem registro de auditoria fica confirmado, e a resposta é `ERRO_INTERNO` com o `correlationId`

### Requirement: Repetição só em operação idempotente
O engine SHALL repetir automaticamente apenas leituras (banco e Blob) e gravações condicionais no Blob que são idempotentes por construção (`If-None-Match: *` com o mesmo conteúdo), no máximo 3 tentativas, com espera exponencial de 200 ms, 400 ms e 800 ms mais variação aleatória de até 100 ms. Gravação no banco, gravação de `estado.json` e de registro de carga MUST NOT ser repetidas automaticamente na mesma requisição. Depois de 5 falhas seguidas no Blob, o engine SHALL abrir o circuito e não chamar o Blob por `engine.blob.circuito-aberto-segundos` (padrão 60), tratando cada operação como Blob fora.

#### Scenario: Circuito aberto
- **WHEN** o Blob falha 5 vezes seguidas
- **THEN** durante os 60 segundos seguintes o engine não chama o Blob, trata cada operação como Blob fora imediatamente e registra `CIRCUITO_BLOB_ABERTO` uma vez

#### Scenario: Falha transitória de leitura no Blob
- **WHEN** a leitura de `estado.json` falha uma vez por erro de rede e funciona na segunda tentativa
- **THEN** a operação segue normalmente, e o log registra a nova tentativa

### Requirement: Estado de script desatualizado mantido
Se o Blob estiver inacessível quando o cache de `estado.json` vencer, a instância SHALL continuar usando o último estado lido com sucesso, sem prazo, e SHALL registrar `ESTADO_SCRIPT_DESATUALIZADO` com nível `AVISO` e a idade do estado, no máximo uma vez por minuto por instância. Na subida, a instância SHALL tentar ler o estado de todos os scripts por até `engine.groovy.espera-subida-segundos` (padrão 300), ficando fora de prontidão enquanto tenta; se conseguir, fica pronta normalmente; se o prazo acabar, fica pronta mesmo assim e registra `ESTADO_SCRIPT_DESCONHECIDO`. Se a instância nunca conseguiu ler o estado, ou se uma versão necessária (ativa ou fixada no cadastro) não estiver compilada em memória e não puder ser lida, a resolução SHALL usar o modelo Java nativo de mesmo nome e registrar `ESTADO_SCRIPT_DESCONHECIDO` com nível `ERRO`. A proveniência de toda construção e consulta SHALL informar `estadoScript`: `ATUAL`, `DESATUALIZADO` ou `DESCONHECIDO`. Se não existir modelo nativo com o nome, a operação falha com `MODELO_FALHOU`.

#### Scenario: Blob fora durante o fechamento
- **WHEN** o Blob fica inacessível por 2 horas durante o fechamento
- **THEN** as construções e consultas seguem com as versões conhecidas, com `estadoScript` = `DESATUALIZADO`, e o log tem `ESTADO_SCRIPT_DESATUALIZADO` no máximo uma vez por minuto

#### Scenario: Blob volta durante a espera da subida
- **WHEN** uma instância nova sobe com o Blob inacessível e ele volta 2 minutos depois
- **THEN** a instância lê o estado, fica pronta e usa as versões ativas, sem nunca ter usado os nativos

#### Scenario: Instância nova com o Blob fora por mais de 5 minutos
- **WHEN** uma instância nova sobe com o Blob inacessível e ele continua fora depois de 5 minutos
- **THEN** a instância fica pronta, as curvas são construídas com os modelos nativos, `estadoScript` = `DESCONHECIDO`, e o log tem `ESTADO_SCRIPT_DESCONHECIDO`

### Requirement: Blob fora nunca bloqueia construção nem consulta
Com o Blob inacessível, construção, reconstrução, edição de pontos, webhook de carga, consulta, interpolação e simulação SHALL funcionar com o que houver: auditoria pendente no log (spec `curve-audit-history`), carga não registrada ou não verificada (spec `curve-load-trigger`) e estado de script desatualizado ou desconhecido. Só as operações que existem para ler ou escrever no Blob (gestão de scripts, consulta de histórico, importação de calendário) MAY falhar com `BLOB_INDISPONIVEL`.

#### Scenario: Construção com o Blob fora
- **WHEN** o Blob está inacessível e o webhook da carga B3 chega
- **THEN** as curvas são construídas, e o log tem `CARGA_NAO_REGISTRADA` e um `AUDITORIA_PENDENTE` por curva

### Requirement: Saúde e prontidão
O engine SHALL expor pelo Actuator:
- `liveness`: só o processo;
- `readiness`: a conexão com o banco e, só durante a espera da subida (até 5 minutos), a leitura do estado dos scripts.

O Blob SHALL aparecer como componente informativo no `health`, sem afetar a prontidão.

#### Scenario: Instância sobe com Blob fora
- **WHEN** uma instância nova sobe com o Blob inacessível e o banco disponível
- **THEN** ela fica `DOWN` em `readiness` por até 5 minutos e depois `UP`, com o `health` mostrando o Blob como `DOWN`

### Requirement: Log de requisição e de dependência
O engine SHALL registrar em log JSON, sempre com `correlationId`:
- `REQUISICAO_CONCLUIDA`: método, rota (com o molde, sem valores de parâmetro sensíveis), status HTTP, `codigoErro` quando houver, usuário, duração em milissegundos;
- `DEPENDENCIA_CHAMADA`, em nível `DEBUG`: dependência, operação, duração e resultado;
- `DEPENDENCIA_LENTA`, em nível `AVISO`, quando uma chamada passar de metade do seu tempo limite;
- `DEPENDENCIA_FALHOU`, em nível `ERRO`: dependência, operação, tentativa, tipo de erro e mensagem, sem stack trace no campo de mensagem (o stack trace vai num campo próprio, só no log);
- `TEMPO_ESGOTADO`: dependência ou operação e o limite configurado.

O log MUST NOT conter token, connection string, conteúdo de script nem corpo de requisição inteiro. Listas de pontos só são permitidas no evento `AUDITORIA_PENDENTE`, que é a cópia de segurança da auditoria; nos demais eventos, só quantidades e `hashPontos`.

#### Scenario: Blob lento
- **WHEN** uma leitura do Blob leva 3 segundos, com limite de 5
- **THEN** o log tem `DEPENDENCIA_LENTA` com a dependência Blob, a operação e a duração

### Requirement: Métricas para alerta
O engine SHALL publicar pelo Micrometer, com as tags `codigo` e `codigoErro` quando aplicáveis:
- contadores de construções por situação e de falhas por `codigoErro`;
- histograma de duração de construção, de interpolação e de cada dependência;
- contador de tempos esgotados por dependência;
- gauge da idade, em segundos, do estado de script em uso;
- gauges de auditorias e registros de carga pendentes de gravação no Blob.

#### Scenario: Alerta de falha de construção
- **WHEN** a construção da `DCL` falha com `INSUMO_INCOMPLETO`
- **THEN** o contador de falhas com `codigo` = `DCL` e `codigoErro` = `INSUMO_INCOMPLETO` é incrementado
