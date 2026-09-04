#!/bin/bash
# ==============================================================================
# Derruba o ambiente local.
#
# Uso: deploy/podman/down.sh [--lite] [-v|--volumes]
#   --lite      derruba o perfil reduzido (compose.lite.yaml)
#   -v/--volumes  remove também os volumes nomeados (banco e tópicos são
#                 apagados; o próximo up parte de um ambiente limpo)
# ==============================================================================
set -euo pipefail

# Ver comentário equivalente em up.sh — evita reescrita de path pelo MSYS no Windows.
export MSYS_NO_PATHCONV=1

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

COMPOSE_FILE="compose.yaml"
REMOVE_VOLUMES="false"

for arg in "$@"; do
  case "${arg}" in
    --lite) COMPOSE_FILE="compose.lite.yaml" ;;
    -v|--volumes) REMOVE_VOLUMES="true" ;;
    *) echo "Argumento desconhecido: ${arg}" >&2; exit 1 ;;
  esac
done

echo "==> Derrubando ambiente (${COMPOSE_FILE}), volumes removidos: ${REMOVE_VOLUMES}"

if [[ "${REMOVE_VOLUMES}" == "true" ]]; then
  podman compose -f "${COMPOSE_FILE}" down -v
else
  podman compose -f "${COMPOSE_FILE}" down
fi

echo "==> Ambiente derrubado"
