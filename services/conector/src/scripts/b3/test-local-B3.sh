#!/usr/bin/env -S bash

set -Eeuo pipefail

COMPOSE_CMD="${COMPOSE_CMD:-docker compose}"
PROJECT_ROOT="${PROJECT_ROOT:-.}"

FUNCTION_HEALTH_URL="${FUNCTION_HEALTH_URL:-${LOCAL_HTTP_SCHEME:-http}://localhost:7071/api/health}"
FUNCTION_URL="${FUNCTION_URL:-${LOCAL_HTTP_SCHEME:-http}://localhost:7071/api/swap-process}"

KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
KAFKA_INIT_CONTAINER="${KAFKA_INIT_CONTAINER:-kafka-init}"
FUNCTION_CONTAINER="${FUNCTION_CONTAINER:-tcen-function-http}"

KAFKA_TOPIC="${KAFKA_TOPIC:-acts-dev-invgua-tcen-precif-curvas-b3-pubsub-negocio-json}"

MAX_WAIT_SECONDS="${MAX_WAIT_SECONDS:-120}"
SLEEP_SECONDS="${SLEEP_SECONDS:-3}"
KAFKA_CONSUME_TIMEOUT_MS="${KAFKA_CONSUME_TIMEOUT_MS:-12000}"

log() {
  echo
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

fail() {
  log "ERRO: $*"
  exit 1
}

cleanup_on_error() {
  local exit_code=$?
  if [[ $exit_code -ne 0 ]]; then
    log "Falha detectada. Últimos logs relevantes:"
    echo
    echo "===== LOGS: ${FUNCTION_CONTAINER} ====="
    docker logs "${FUNCTION_CONTAINER}" --tail 200 2>/dev/null || true
    echo
    echo "===== LOGS: ${KAFKA_CONTAINER} ====="
    docker logs "${KAFKA_CONTAINER}" --tail 100 2>/dev/null || true
    echo
    echo "===== LOGS: ${KAFKA_INIT_CONTAINER} ====="
    docker logs "${KAFKA_INIT_CONTAINER}" --tail 100 2>/dev/null || true
  fi
  exit "$exit_code"
}

trap cleanup_on_error EXIT

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "Comando obrigatório não encontrado: $1"
}

wait_for_http() {
  local url="$1"
  local name="$2"
  local max_wait="${3:-$MAX_WAIT_SECONDS}"
  local waited=0

  log "Aguardando ${name} em ${url}"

  until curl -fsS "$url" >/dev/null 2>&1; do
    sleep "$SLEEP_SECONDS"
    waited=$((waited + SLEEP_SECONDS))

    if (( waited >= max_wait )); then
      fail "Timeout aguardando ${name} em ${url}"
    fi
  done

  log "${name} disponível"
}

wait_for_container_running() {
  local container_name="$1"
  local max_wait="${2:-$MAX_WAIT_SECONDS}"
  local waited=0

  log "Aguardando container ${container_name} ficar em execução"

  while true; do
    local running
    local status

    running="$(docker inspect -f '{{.State.Running}}' "${container_name}" 2>/dev/null || echo false)"
    status="$(docker inspect -f '{{.State.Status}}' "${container_name}" 2>/dev/null || echo unknown)"

    if [[ "${running}" == "true" ]]; then
      log "Container ${container_name} em execução"
      return 0
    fi

    if [[ "${status}" == "exited" || "${status}" == "dead" ]]; then
      echo
      echo "===== LOGS: ${container_name} ====="
      docker logs "${container_name}" --tail 200 2>/dev/null || true
      fail "Container ${container_name} não permaneceu em execução. Status=${status}"
    fi

    sleep "$SLEEP_SECONDS"
    waited=$((waited + SLEEP_SECONDS))

    if (( waited >= max_wait )); then
      fail "Timeout aguardando container ${container_name}"
    fi
  done
}

wait_for_container_exit_zero() {
  local container_name="$1"
  local max_wait="${2:-$MAX_WAIT_SECONDS}"
  local waited=0

  log "Aguardando container ${container_name} finalizar com sucesso"

  while true; do
    local state
    local exit_code

    state="$(docker inspect -f '{{.State.Status}}' "${container_name}" 2>/dev/null || echo unknown)"
    exit_code="$(docker inspect -f '{{.State.ExitCode}}' "${container_name}" 2>/dev/null || echo 999)"

    if [[ "$state" == "exited" && "$exit_code" == "0" ]]; then
      log "Container ${container_name} finalizou com sucesso"
      return 0
    fi

    if [[ "$state" == "exited" && "$exit_code" != "0" ]]; then
      echo
      echo "===== LOGS: ${container_name} ====="
      docker logs "${container_name}" --tail 200 2>/dev/null || true
      fail "Container ${container_name} finalizou com erro. ExitCode=${exit_code}"
    fi

    sleep "$SLEEP_SECONDS"
    waited=$((waited + SLEEP_SECONDS))

    if (( waited >= max_wait )); then
      fail "Timeout aguardando finalização do container ${container_name}"
    fi
  done
}

wait_for_kafka_topic() {
  local topic="$1"
  local max_wait="${2:-$MAX_WAIT_SECONDS}"
  local waited=0

  log "Aguardando tópico Kafka ${topic} ficar disponível"

  until docker exec "${KAFKA_CONTAINER}" kafka-topics \
    --bootstrap-server kafka:9092 \
    --describe \
    --topic "${topic}" >/dev/null 2>&1; do
    sleep "$SLEEP_SECONDS"
    waited=$((waited + SLEEP_SECONDS))

    if (( waited >= max_wait )); then
      docker exec "${KAFKA_CONTAINER}" kafka-topics --bootstrap-server kafka:9092 --list || true
      fail "Timeout aguardando tópico Kafka ${topic}"
    fi
  done

  log "Tópico Kafka ${topic} disponível"
}

assert_json_field() {
  local json="$1"
  local expected_fragment="$2"
  local description="$3"

  if [[ "$json" != *"$expected_fragment"* ]]; then
    echo "$json"
    fail "Validação falhou: ${description}"
  fi

  log "Validação OK: ${description}"
}

extract_total_records() {
  local response="$1"
  echo "$response" | grep -o '"totalRecords":[0-9]\+' | head -1 | cut -d: -f2
}

consume_kafka_messages() {
  local timeout_ms="${1:-10000}"

  docker exec "${KAFKA_CONTAINER}" kafka-console-consumer \
    --bootstrap-server kafka:9092 \
    --topic "${KAFKA_TOPIC}" \
    --from-beginning \
    --timeout-ms "${timeout_ms}" 2>/dev/null || true
}

validate_success_scenario() {
  local get_response="$1"
  local post_response="$2"

  assert_json_field "${get_response}" "\"success\":true" "GET retorna campo success"
  assert_json_field "${post_response}" "\"success\":true" "POST retorna campo success"

  local get_total_records
  local post_total_records

  get_total_records="$(extract_total_records "${get_response}")"
  post_total_records="$(extract_total_records "${post_response}")"

  get_total_records="${get_total_records:-0}"
  post_total_records="${post_total_records:-0}"

  log "GET totalRecords=${get_total_records}"
  log "POST totalRecords=${post_total_records}"

  if [[ "${get_total_records}" == "0" && "${post_total_records}" == "0" ]]; then
    fail "A function executou com success=true, porém totalRecords=0. Verifique o conteúdo retornado pela origem B3."
  fi

  log "Listando tópicos Kafka"
  docker exec "${KAFKA_CONTAINER}" kafka-topics --bootstrap-server kafka:9092 --list || true

  log "Consumindo mensagens do tópico ${KAFKA_TOPIC}"
  local kafka_messages
  kafka_messages="$(consume_kafka_messages "${KAFKA_CONSUME_TIMEOUT_MS}")"
  echo "${kafka_messages}"

  if [[ -z "${kafka_messages}" ]]; then
    fail "Nenhuma mensagem encontrada no tópico ${KAFKA_TOPIC}"
  fi

  assert_json_field "${kafka_messages}" "\"nomeTabela\"" "mensagens publicadas contêm nomeTabela"
  assert_json_field "${kafka_messages}" "\"values\"" "mensagens publicadas contêm values"
  assert_json_field "${kafka_messages}" "\"ticker\"" "mensagens publicadas contêm values.ticker"
  assert_json_field "${kafka_messages}" "\"valor\"" "mensagens publicadas contêm values.valor"
  assert_json_field "${kafka_messages}" "\"fatorDiario\"" "mensagens publicadas contêm values.fatorDiario"
  assert_json_field "${kafka_messages}" "\"fatorAcumulado\"" "mensagens publicadas contêm values.fatorAcumulado"
}

require_command docker
require_command curl

if [[ -z "${B3_SWAP_URL:-}" ]]; then
  fail "Configure B3_SWAP_URL com uma URL HTTPS antes de executar o teste local. Exemplo: export B3_SWAP_URL=https://..."
fi

if [[ "${B3_SWAP_URL}" != https://* ]]; then
  fail "B3_SWAP_URL deve usar HTTPS. Valor atual: ${B3_SWAP_URL}"
fi

log "Entrando na raiz do projeto: ${PROJECT_ROOT}"
cd "${PROJECT_ROOT}"

log "Derrubando ambiente anterior"
${COMPOSE_CMD} down -v --remove-orphans

log "Subindo ambiente"
${COMPOSE_CMD} up -d

wait_for_container_running "zookeeper"
wait_for_container_running "${KAFKA_CONTAINER}"
wait_for_container_exit_zero "${KAFKA_INIT_CONTAINER}"
wait_for_kafka_topic "${KAFKA_TOPIC}"
wait_for_container_running "${FUNCTION_CONTAINER}"

wait_for_http "${FUNCTION_HEALTH_URL}" "Function HTTP"

log "Testando GET da function"
FUNCTION_GET_RESPONSE="$(curl -fsS -X GET "${FUNCTION_URL}")"
echo "${FUNCTION_GET_RESPONSE}"

log "Testando POST da function"
FUNCTION_POST_RESPONSE="$(curl -fsS -X POST "${FUNCTION_URL}" -H "Content-Type: application/json" -d '{}')"
echo "${FUNCTION_POST_RESPONSE}"

validate_success_scenario "${FUNCTION_GET_RESPONSE}" "${FUNCTION_POST_RESPONSE}"

log "Ambiente validado com sucesso"
log "URLs úteis:"
echo "  Function health : ${FUNCTION_HEALTH_URL}"
echo "  Function        : ${FUNCTION_URL}"

log "Para acompanhar logs da function:"
echo "  docker logs -f ${FUNCTION_CONTAINER}"

log "Para consumir Kafka manualmente:"
echo "  docker exec -it ${KAFKA_CONTAINER} kafka-console-consumer --bootstrap-server kafka:9092 --topic ${KAFKA_TOPIC} --from-beginning"
