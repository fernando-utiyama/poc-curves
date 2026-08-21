# Contratos de Eventos da Plataforma de Curvas Financeiras

Este diretório contém a especificação formal dos eventos assíncronos e tópicos Kafka utilizados na plataforma de curvas financeiras. Todos os componentes que produzem ou consomem eventos devem aderir estritamente às diretrizes descritas neste documento.

---

## 1. O Envelope Comum e Tipagem Estrita de Valores de Mercado

Todos os eventos trafegados na plataforma são envelopados pela estrutura definida em [`envelope.schema.json`](envelope.schema.json). O envelope padroniza metadados vitais de auditoria, rastreabilidade, versionamento e particionamento (`eventId`, `correlationId`, `source`, `dataset`, `referenceDate`, `producedAt`, `schemaVersion`, `payloadKind`, `loteId`, `sequencia`, `totalBlocos`).

### Por que valores de mercado trafegam SEMPRE como string?

Valores sujeitos a políticas de arredondamento e precisão do mercado financeiro (taxas de juros, preços unitários, cotações, fatores de desconto, spreads e limiares de validação) **NUNCA** devem ser declarados como `number` em JSON, trafegando estritamente como string numérica (`DecimalString`).

- **Motivação Técnica:** O tipo `number` em JSON e na grande maioria dos runtimes (como o tipo `number`/`double` IEEE-754 de 64 bits em JavaScript/V8) tem limite de 53 bits de mantissa (~15 a 17 dígitos decimais significativos) e opera em base 2.
- **Risco Financeiro:** Taxas de juros de contratos futuros e derivativos operam com até 12 ou mais casas decimais exatas. O uso de ponto flutuante binário introduz erros infinitesimais de truncamento/arredondamento que se propagam e se multiplicam de forma inaceitável durante operações de interpolação e bootstrapping de curvas.
- **Implementação:** Toda aplicação deve desserializar campos numéricos financeiros diretamente para tipos decimais de precisão arbitrária (ex.: `BigDecimal` em Java/Kotlin, `Decimal` em Python/C#).

---

## 2. As Três Faixas de Ingestão e o Isolamento Operacional

A ingestão de dados brutos de mercado é segregada em três tópicos independentes em [`topics.yaml`](topics.yaml):

1. `marketdata.rotina.v1`: Ingestões agendadas automaticamente (cron diário).
2. `marketdata.prioritaria.v1`: Disparos manuais e intervenções corretivas via interface de operação.
3. `marketdata.massa.v1`: Cargas históricas de grande porte, migrações e rotinas de backfill.

### Por que três faixas separadas?

A chave de partição dos tópicos de ingestão é composta por `source|dataset|referenceDate`. 

Se existisse uma única fila de ingestão e um arquivo de rotina ficasse travado ou um backfill massivo estivesse em execução, uma tentativa de **redisparo manual prioritário** pelo operador entraria **ATRÁS** da mensagem travada/lenta na mesma partição Kafka, inviabilizando o desbloqueio da operação.

A separação em três faixas físicas independentes proporciona:
- **Isolamento de Consumidores e Threads:** Grupos de consumo distintos (`curve-processor-rotina`, `curve-processor-prioritaria`, `curve-processor-massa`) com pools de workers e instâncias isoladas.
- **Prioridade Garantida:** Mensagens na faixa prioritária têm vazão imediata sem disputa de recursos com cargas pesadas de histórico ou rotinas travadas.

---

## 3. Identificação de Lote e Bloco: Integridade e Completude

Grandes arquivos adquiridos pelas fontes são fatiados em blocos estruturais pelo feeder. O envelope transporta a tripla de controle:
- `loteId`: Identificador determinístico do lote completo (UUIDv5 gerado a partir de `source + dataset + referenceDate + sha256(conteúdo_adquirido_completo)`).
- `sequencia`: Número ordinal do bloco atual (1, 2, ..., `totalBlocos`).
- `totalBlocos`: Quantidade total de blocos esperados para o lote.

### Regra de Ouro da Construção de Curvas
Um lote só é considerado pronto quando **TODOS** os seus blocos (de `1` a `totalBlocos`) forem recebidos, validados e persistidos com sucesso no banco de dados.

> **Lote incompleto NÃO gera pedido de construção de curva.**  
> É preferível não publicar uma curva (disparando alerta de ausência de insumo) a calcular e publicar uma curva baseada em dados parciais ou com metade dos contratos/vértices do pregão.

---

## 4. Idempotência e Semântica de Entrega

O modelo de mensageria do Kafka adota semântica **at-least-once** (pelo menos uma vez). Podem ocorrer duplicidades decorrentes de rebalances, retentativas de rede ou falhas pós-processamento antes do commit do offset.

Todo consumidor é **obrigatoriamente idempotente**:
- O `eventId` (UUIDv4 único) deve ser registrado e verificado em tabela/índice de controle de idempotência antes de aplicar alterações de estado.
- A recepção de eventos duplicados deve ser tratada como operação bem-sucedida (no-op), confirmando o offset no Kafka sem gerar efeitos colaterais.

---

## 5. INVARIANTE DE AVANÇO DE OFFSET

> ### ⚠️ REGRA FUNDAMENTAL E INVIOLÁVEL
> **Nenhum caminho de tratamento de erro em nenhum consumidor pode terminar sem avançar o offset.**
> 
> Toda e qualquer mensagem consumida da partição deve ter seu ciclo finalizado de apenas duas formas possíveis:
> 1. **Sucesso:** A mensagem foi processada com êxito e o offset é confirmado (*committed*).
> 2. **Desvio para DLQ:** A mensagem falhou em todas as tentativas permitidas, é publicada na **Dead-Letter Queue (DLQ)** exclusiva do grupo com seus respectivos cabeçalhos de diagnóstico, e o offset na partição de origem é **CONFIRMADO**.
>
> **Retentativas infinitas no mesmo tópico/partição são TERMINANTEMENTE PROIBIDAS.**

### Os Quatro Modos de Travamento e Suas Defesas

| Modo de Travamento | O que acontece | Defesa Obrigatória |
| :--- | :--- | :--- |
| **(a) Falha na desserialização** | O payload não segue o formato JSON ou o schema esperado. A falha ocorre na camada de infraestrutura/deserializador antes de alcançar o código de negócio. Se lançar exceção não tratada, o consumidor entra em laço infinito relendo o mesmo byte inválido. | Utilizar desserializadores com *Error-Handling Deserializer* (que encapsulam a falha no cabeçalho ou em objeto de erro em vez de lançar exceção fatal), encaminhando o registro bruto corrompido diretamente à DLQ e avançando o offset. |
| **(b) Retentativa sem terminador** | Erro de validação de negócio ou falha transitória sem política de esgotamento. O consumidor rejeita a mensagem e ela é reprocessada indefinidamente. | Toda política de retry deve ter um terminador determinístico (*DeadLetterPublishingRecoverer*), que publica a mensagem na DLQ dedicada do grupo após esgotar o limite configurado de tentativas e confirma o offset de origem. |
| **(c) Mensagem lenta demais** | O tempo de processamento de um lote de mensagens excede `maxPollIntervalMs`. O broker assume que o consumidor morreu, expulsa-o do grupo e dispara um *rebalance*. O consumidor seguinte pega o mesmo offset, estoura o tempo novamente, gerando um ciclo infinito de *livelock*. | Calibrar `maxPollRecords` para volumes pequenos e garantir que o processamento do lote inteiro termine confortavelmente abaixo de `maxPollIntervalMs`. |
| **(d) Chamada externa sem timeout** | Uma requisição HTTP/gRPC, consulta a banco de dados ou acesso a disco congela indefinidamente sem timeout explícito, travando a thread consumidora. | Nenhum mecanismo interno do broker ou do SDK resolve esse problema: é **disciplina estrita de aplicação**. Todas as chamadas de I/O externas DEVEM possuir timeouts rígidos configurados (`externalCallTimeoutMs`), que precisam ser significativamente menores do que `maxPollIntervalMs`. |

---

## 6. Limites de Tempo e Retentativa com Teto de SLA

O consumo e a retentativa de mensagens devem obedecer aos limites definidos em `topics.yaml`:

- `maxPollIntervalMs`: Janela máxima permitida entre invocações de poll (padrão: 300.000 ms / 5 min).
- `externalCallTimeoutMs`: Timeout rígido para qualquer chamada externa síncrona (padrão: 15.000 ms / 15 s). **Regra:** `externalCallTimeoutMs < maxPollIntervalMs`.

### Retentativa com Teto de Tempo (Deadline de Publicação)
A política de retentativa não deve considerar apenas um contador fixo de tentativas (ex.: 3 retries). 

O número de retentativas deve ser dinamicamente limitado pelo **tempo restante até o horário limite de publicação da curva** (`publishDeadline` presente no evento):
$$\text{Teto de Execução} = \min(\text{TentativasRestantes} \times \text{Backoff}, \text{publishDeadline} - \text{InstanteAtual})$$

Se o deadline operacional estiver na iminência de estourar, o processamento deve falhar rapidamente para a DLQ para acionar o alerta operacional humano antes que o SLA regulatório seja violado.

---

## 7. Política de Evolução de Contratos

Para garantir compatibilidade contínua entre produtores e consumidores desacoplados:

1. **Mudanças Compatíveis (Retrocompatibilidade Total):**
   - Apenas a adição de **campos opcionais** é permitida na mesma versão de schema (`v1`).
   - É proibido remover campos obrigatórios, alterar tipos de dados ou renomear propriedades existentes.
   - O consumidor deve ignorar campos desconhecidos adicionais quando aplicável, mantendo a regra de integridade do envelope.

2. **Mudanças Incompatíveis (Breaking Changes):**
   - Alterações de tipos, exigência de novos campos obrigatórios ou mudanças estruturais exigem a criação de uma nova versão do contrato e de um novo tópico Kafka com sufixo de versão (ex.: `marketdata.rotina.v2`).
   - Durante o período de migração, as versões `v1` e `v2` devem **coexistir em paralelo**, permitindo a migração gradual de produtores e consumidores sem indisponibilidade da plataforma.
