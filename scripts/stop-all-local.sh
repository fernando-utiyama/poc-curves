#!/bin/bash
# ==============================================================================
# Finaliza todas as aplicações da Plataforma de Curvas rodando nativamente.
# ==============================================================================
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
PID_FILE="${REPO_ROOT}/.local-processes.json"
TARGET_PORTS=(8080 8081 8082 8083 8084 8091 4200)

echo "==> Finalizando aplicações locais..."

if [[ -f "${PID_FILE}" ]]; then
  pids=$(grep -oE '"Pid":[ ]*[0-9]+' "${PID_FILE}" | awk -F: '{print $2}' | tr -d ' ' || true)
  for pid in ${pids}; do
    if kill -0 "${pid}" 2>/dev/null; then
      kill -9 "${pid}" 2>/dev/null || true
      echo "  [-] Processo PID ${pid} finalizado."
    fi
  done
  rm -f "${PID_FILE}"
fi

for port in "${TARGET_PORTS[@]}"; do
  pid=$(lsof -ti tcp:"${port}" 2>/dev/null || true)
  if [[ -n "${pid}" ]]; then
    kill -9 "${pid}" 2>/dev/null || true
    echo "  [-] Processo na porta ${port} (PID ${pid}) finalizado."
  fi
done

echo "==> Todas as aplicações locais foram finalizadas."
