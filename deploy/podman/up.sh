#!/bin/bash
# ==============================================================================
# Sobe o ambiente local: infraestrutura -> espera ativa por prontidão real de
# cada serviço -> criação do banco -> bootstrap de tópicos -> migração Flyway.
# Cada etapa termina com código zero antes da próxima começar; se qualquer
# etapa falhar, a subida para e reporta o que travou (D11 / spec local-runtime).
#
# Uso: deploy/podman/up.sh [--lite]
#   --lite  sobe o perfil reduzido (compose.lite.yaml): só Kafka e SQL Server.
# ==============================================================================
set -euo pipefail

# No Git Bash (MSYS) no Windows, argumentos com barra inicial (ex.: os
# caminhos de sqlcmd/kafka-topics.sh abaixo) são reescritos para caminho
# Windows antes de chegar no `podman exec`, quebrando o comando dentro do
# container Linux. Sem efeito em bash nativo (Linux/macOS).
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

PROFILE="full"
COMPOSE_FILE="compose.yaml"
if [[ "${1:-}" == "--lite" ]]; then
  PROFILE="lite"
  COMPOSE_FILE="compose.lite.yaml"
fi

echo "==> Perfil: ${PROFILE} (${COMPOSE_FILE})"

wait_for() {
  local description="$1" timeout_s="$2"; shift 2
  local waited=0
  echo "==> Aguardando: ${description}"
  until "$@" >/dev/null 2>&1; do
    sleep 3
    waited=$((waited + 3))
    if (( waited >= timeout_s )); then
      echo "ERRO: tempo limite (${timeout_s}s) excedido aguardando: ${description}" >&2
      return 1
    fi
  done
  echo "    ok (${waited}s)"
}

echo "==> Subindo infraestrutura em background"
podman compose -f "${COMPOSE_FILE}" up -d kafka sqlserver
if [[ "${PROFILE}" == "full" ]]; then
  podman compose -f "${COMPOSE_FILE}" up -d redis keycloak
fi

wait_for "SQL Server pronto" 180 \
  podman exec curvas-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa \
    -P "${MSSQL_SA_PASSWORD:-CurvasP0c!Local}" -C -N -Q "SELECT 1"

wait_for "Kafka pronto" 120 \
  podman exec curvas-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092

if [[ "${PROFILE}" == "full" ]]; then
  wait_for "Redis pronto" 60 \
    podman exec curvas-redis redis-cli ping
  wait_for "Keycloak pronto" 120 \
    curl -sf http://localhost:9000/health/ready
fi

echo "==> Criando banco curvasdb (se necessário)"
podman compose -f "${COMPOSE_FILE}" run --rm sqlserver-init-db
echo "    ok"

echo "==> Aplicando migrações Flyway"
podman compose -f "${COMPOSE_FILE}" run --rm flyway-migrate
echo "    ok"

echo "==> Criando tópicos do catálogo Kafka"
podman compose -f "${COMPOSE_FILE}" run --rm kafka-topics-bootstrap
echo "    ok"

echo "==> Ambiente local pronto (perfil: ${PROFILE})"
