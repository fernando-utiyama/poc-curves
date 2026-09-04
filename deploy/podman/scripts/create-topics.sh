#!/bin/bash
# ==============================================================================
# Cria no broker os tópicos declarados no catálogo (contracts/events/topics.yaml).
# Roda dentro do container kafka-topics-bootstrap (imagem apache/kafka, que traz
# o kafka-topics.sh); o broker sobe com KAFKA_AUTO_CREATE_TOPICS_ENABLE=false,
# então nenhum tópico existe até este script rodar.
#
# MANUTENÇÃO: esta lista espelha contracts/events/topics.yaml. Qualquer tópico
# ou DLQ acrescentado lá precisa ser acrescentado aqui — é o mesmo contrato,
# só que em forma de comando em vez de YAML. O teste de conformidade que audita
# esse acoplamento é tarefa 7.9 (contagem de partições vs. catálogo).
# ==============================================================================
set -euo pipefail

BOOTSTRAP="${KAFKA_BOOTSTRAP_SERVER:-kafka:9092}"
KAFKA_TOPICS=/opt/kafka/bin/kafka-topics.sh

# name:partitions:replicationFactor:retentionMs
TOPICS=(
  "marketdata.rotina.v1:6:1:604800000"
  "marketdata.prioritaria.v1:6:1:604800000"
  "marketdata.massa.v1:6:1:604800000"
  "marketdata.normalized.v1:6:1:604800000"
  "curve.published.v1:6:1:2592000000"
  "marketdata.rotina.v1.curve-processor-rotina.dlq:6:1:2592000000"
  "marketdata.prioritaria.v1.curve-processor-prioritaria.dlq:6:1:2592000000"
  "marketdata.massa.v1.curve-processor-massa.dlq:6:1:2592000000"
  "marketdata.normalized.v1.curve-orchestrator-normalized.dlq:6:1:2592000000"
  "curve.published.v1.curve-orchestrator-published.dlq:6:1:2592000000"
)

echo "Aguardando broker em ${BOOTSTRAP}..."
until "${KAFKA_TOPICS}" --bootstrap-server "${BOOTSTRAP}" --list >/dev/null 2>&1; do
  sleep 2
done

for entry in "${TOPICS[@]}"; do
  IFS=':' read -r name partitions rf retention_ms <<< "${entry}"
  echo "Criando tópico ${name} (partitions=${partitions}, rf=${rf}, retention.ms=${retention_ms})"
  "${KAFKA_TOPICS}" --bootstrap-server "${BOOTSTRAP}" \
    --create --if-not-exists \
    --topic "${name}" \
    --partitions "${partitions}" \
    --replication-factor "${rf}" \
    --config "retention.ms=${retention_ms}"
done

echo "Catálogo de tópicos aplicado com sucesso: ${#TOPICS[@]} tópicos."
