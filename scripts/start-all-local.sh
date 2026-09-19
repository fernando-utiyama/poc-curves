#!/bin/bash
# ==============================================================================
# Inicia todas as aplicações da Plataforma de Curvas nativamente (sem Docker/Podman).
# ==============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-1433}"
DB_NAME="${DB_NAME:-curvasdb}"
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}"
REDIS_HOST="${REDIS_HOST:-localhost}"
REDIS_PORT="${REDIS_PORT:-6379}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://localhost:8180}"
MAX_MEM="${MAX_MEM:-384m}"

echo "=================================================================="
echo "  PLATAFORMA DE CURVAS - INICIALIZAÇÃO LOCAL (SEM DOCKER/PODMAN) "
echo "=================================================================="

# Finalizar instâncias anteriores
"${SCRIPT_DIR}/stop-all-local.sh"

mkdir -p "${REPO_ROOT}/logs"

DATASOURCE_URL="jdbc:sqlserver://${DB_HOST}:${DB_PORT};databaseName=${DB_NAME};trustServerCertificate=true;encrypt=true"
JWT_URI="${KEYCLOAK_URL}/realms/curvas/protocol/openid-connect/certs"

JAVA_COMMON_ARGS=(
  "-Xms128m"
  "-Xmx${MAX_MEM}"
  "-Dspring.datasource.url=${DATASOURCE_URL}"
  "-Dspring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS}"
  "-Dspring.security.oauth2.resourceserver.jwt.jwk-set-uri=${JWT_URI}"
)

# 1. Feeder: conector
echo "  [+] Iniciando conector (porta 8091)..."
(
  cd "${REPO_ROOT}/services/conector"
  PORT=8091 KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS}" node dist/main-http.js > "${REPO_ROOT}/logs/conector.log" 2>&1 &
  echo "{\"Name\":\"conector\",\"Port\":8091,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"
)

# 2. curve-processor
echo "  [+] Iniciando curve-processor (porta 8081)..."
PROC_JAR=$(ls "${REPO_ROOT}/services/curve-processor/target/curve-processor-"*.jar | grep -v "plain" | head -1)
java "${JAVA_COMMON_ARGS[@]}" -jar "${PROC_JAR}" > "${REPO_ROOT}/logs/curve-processor.log" 2>&1 &
echo "{\"Name\":\"curve-processor\",\"Port\":8081,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"

# 3. curve-api
echo "  [+] Iniciando curve-api (porta 8082)..."
API_JAR=$(ls "${REPO_ROOT}/services/curve-api/target/curve-api-"*.jar | grep -v "plain" | head -1)
java "${JAVA_COMMON_ARGS[@]}" -jar "${API_JAR}" > "${REPO_ROOT}/logs/curve-api.log" 2>&1 &
echo "{\"Name\":\"curve-api\",\"Port\":8082,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"

# 4. curve-engine
echo "  [+] Iniciando curve-engine (porta 8083)..."
ENGINE_JAR=$(ls "${REPO_ROOT}/services/curve-engine/target/curve-engine-"*.jar | grep -v "plain" | head -1)
java "${JAVA_COMMON_ARGS[@]}" \
  "-Dspring.data.redis.host=${REDIS_HOST}" \
  "-Dspring.data.redis.port=${REDIS_PORT}" \
  "-Dservices.curve-orchestrator.url=http://localhost:8084" \
  -jar "${ENGINE_JAR}" > "${REPO_ROOT}/logs/curve-engine.log" 2>&1 &
echo "{\"Name\":\"curve-engine\",\"Port\":8083,\"Pid\":$!} " >> "${REPO_ROOT}/.local-processes.json"

# 5. curve-orchestrator
echo "  [+] Iniciando curve-orchestrator (porta 8084)..."
ORCH_JAR=$(ls "${REPO_ROOT}/services/curve-orchestrator/target/curve-orchestrator-"*.jar | grep -v "plain" | head -1)
java "${JAVA_COMMON_ARGS[@]}" \
  "-Dservices.function-marketdata.url=http://localhost:8091" \
  "-Dservices.curve-processor.url=http://localhost:8081" \
  "-Dservices.curve-engine.url=http://localhost:8083" \
  -jar "${ORCH_JAR}" > "${REPO_ROOT}/logs/curve-orchestrator.log" 2>&1 &
echo "{\"Name\":\"curve-orchestrator\",\"Port\":8084,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"

# 6. curve-bff
echo "  [+] Iniciando curve-bff (porta 8080)..."
BFF_JAR=$(ls "${REPO_ROOT}/services/curve-bff/target/curve-bff-"*.jar | grep -v "plain" | head -1)
java "${JAVA_COMMON_ARGS[@]}" \
  "-Dcors.allowed-origins=http://localhost:4200" \
  "-Dservices.curve-api.url=http://localhost:8082" \
  "-Dservices.curve-engine.url=http://localhost:8083" \
  "-Dservices.curve-orchestrator.url=http://localhost:8084" \
  -jar "${BFF_JAR}" > "${REPO_ROOT}/logs/curve-bff.log" 2>&1 &
echo "{\"Name\":\"curve-bff\",\"Port\":8080,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"

# 7. curve-web-ui
echo "  [+] Iniciando curve-web-ui (porta 4200)..."
(
  cd "${REPO_ROOT}/web/curve-web-ui"
  npx ng serve --port 4200 --proxy-config proxy.conf.json > "${REPO_ROOT}/logs/curve-web-ui.log" 2>&1 &
  echo "{\"Name\":\"curve-web-ui\",\"Port\":4200,\"Pid\":$!}" >> "${REPO_ROOT}/.local-processes.json"
)

echo "==> Todas as aplicações foram iniciadas em background."
echo "    Consulte os logs na pasta: ${REPO_ROOT}/logs/"
echo "    Para encerrar: ${SCRIPT_DIR}/stop-all-local.sh"
