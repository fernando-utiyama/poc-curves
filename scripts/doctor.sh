#!/bin/bash
# ==============================================================================
# Diagnóstico de pré-requisitos do ambiente local (deploy/podman).
# Verifica versão do Podman, memória disponível para a VM (Windows/Mac),
# portas livres e o socket usado por Testcontainers — e aponta exatamente
# o que falta e como corrigir, sem tentar corrigir nada sozinho.
#
# Uso: scripts/doctor.sh
# Saída: código zero se tudo passou; código 1 se algum requisito falhou.
# ==============================================================================
set -uo pipefail

MIN_PODMAN_MAJOR=5
RECOMMENDED_MACHINE_MEMORY_MB=6144
MIN_MACHINE_MEMORY_MB=4096
PORTS_TO_CHECK="1433 19092 6379 8180 9000"

FAILED=0
WARNED=0

ok()   { echo "  [OK]   $1"; }
warn() { echo "  [AVISO] $1"; WARNED=1; }
fail() { echo "  [FALHA] $1"; FAILED=1; }

echo "== Podman =="
if ! command -v podman >/dev/null 2>&1; then
  fail "podman não encontrado no PATH. Instale o Podman e reabra o terminal (o PATH só é atualizado em sessões novas)."
else
  PODMAN_VERSION="$(podman --version | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1)"
  PODMAN_MAJOR="${PODMAN_VERSION%%.*}"
  if [[ -z "${PODMAN_VERSION}" ]]; then
    warn "não foi possível ler a versão do podman a partir de 'podman --version'."
  elif (( PODMAN_MAJOR < MIN_PODMAN_MAJOR )); then
    fail "Podman ${PODMAN_VERSION} encontrado, mas é preciso >= ${MIN_PODMAN_MAJOR}.0.0 (KRaft do Kafka e 'podman compose --include' dependem disso)."
  else
    ok "Podman ${PODMAN_VERSION}"
  fi
fi

echo "== Máquina Podman (Windows/Mac) =="
if command -v podman >/dev/null 2>&1; then
  HOST_OS="$(podman info --format '{{.Host.OS}}' 2>/dev/null || echo unknown)"
  if podman machine list --format '{{.Name}}' >/dev/null 2>&1 && [[ -n "$(podman machine list --format '{{.Name}}' 2>/dev/null)" ]]; then
    RUNNING="$(podman machine list --format '{{.Name}} {{.Running}}' 2>/dev/null | head -1)"
    if [[ "${RUNNING}" == *"true"* ]]; then
      ok "máquina Podman em execução (${RUNNING%% *})"
    else
      fail "máquina Podman existe mas não está rodando. Corrija com: podman machine start"
    fi
    # No backend WSL (padrão no Windows), Resources.Memory é um valor estático de
    # criação que o WSL ignora em runtime — a alocação real é dinâmica, limitada
    # pelo .wslconfig do usuário (ou pelo teto padrão do WSL na ausência dele).
    # Medir a memória real dentro da VM em vez de confiar no campo estático.
    MACHINE_MEM="$(MSYS_NO_PATHCONV=1 podman machine ssh -- free -m 2>/dev/null | awk '/^Mem:/{print $2}')"
    if [[ -z "${MACHINE_MEM}" ]]; then
      MACHINE_MEM="$(podman machine inspect --format '{{.Resources.Memory}}' 2>/dev/null | head -1)"
    fi
    if [[ "${MACHINE_MEM}" =~ ^[0-9]+$ ]]; then
      if (( MACHINE_MEM < MIN_MACHINE_MEMORY_MB )); then
        fail "máquina Podman com ${MACHINE_MEM} MiB de memória; mínimo ${MIN_MACHINE_MEMORY_MB} MiB (SQL Server sozinho consome ~2 GiB). Corrija com: podman machine stop && podman machine set --memory ${RECOMMENDED_MACHINE_MEMORY_MB} && podman machine start"
      elif (( MACHINE_MEM < RECOMMENDED_MACHINE_MEMORY_MB )); then
        warn "máquina Podman com ${MACHINE_MEM} MiB; funciona, mas ${RECOMMENDED_MACHINE_MEMORY_MB} MiB é o recomendado para o perfil completo (Kafka + SQL Server + Redis + Keycloak)."
      else
        ok "memória da máquina: ${MACHINE_MEM} MiB"
      fi
    else
      warn "não foi possível ler a memória alocada da máquina Podman."
    fi
  else
    if [[ "${HOST_OS}" == "linux" ]]; then
      ok "Podman nativo em Linux, sem VM (nada a verificar aqui)."
    else
      fail "nenhuma máquina Podman encontrada. Corrija com: podman machine init && podman machine start"
    fi
  fi
fi

echo "== Portas livres (${PORTS_TO_CHECK}) =="
for port in ${PORTS_TO_CHECK}; do
  if (exec 3<>"/dev/tcp/127.0.0.1/${port}") 2>/dev/null; then
    exec 3<&- 3>&- 2>/dev/null
    warn "porta ${port} já está em uso. Se for um container deste projeto de uma subida anterior, ignore; senão, libere a porta antes de subir o ambiente."
  else
    ok "porta ${port} livre"
  fi
done

echo "== Socket Podman para Testcontainers =="
if command -v podman >/dev/null 2>&1; then
  SOCK_PATH="$(podman info --format '{{.Host.RemoteSocket.Path}}' 2>/dev/null)"
  if [[ -z "${SOCK_PATH}" ]]; then
    fail "não foi possível obter o socket remoto do Podman. Rode 'podman machine start' (Windows/Mac) ou habilite o socket de usuário (Linux: systemctl --user enable --now podman.socket)."
  else
    # O caminho retornado já vem com o esquema (ex.: unix:///run/...); não prefixar de novo.
    SOCK_URI="${SOCK_PATH}"
    [[ "${SOCK_URI}" != unix://* ]] && SOCK_URI="unix://${SOCK_URI}"
    ok "socket: ${SOCK_PATH}"
    if podman machine list --format '{{.Name}}' >/dev/null 2>&1 && [[ -n "$(podman machine list --format '{{.Name}}' 2>/dev/null)" ]]; then
      echo "     Testcontainers (Windows/Mac, via máquina Podman): 'podman machine start' já expõe um socket compatível com Docker via API forwarding;"
      echo "     se o Testcontainers não detectar sozinho, defina DOCKER_HOST=${SOCK_URI} explicitamente."
      echo "     Se o Ryuk (reaper de containers de teste) falhar em modo rootless, defina TESTCONTAINERS_RYUK_DISABLED=true."
    else
      echo "     Testcontainers (Linux nativo): export DOCKER_HOST=${SOCK_URI}"
    fi
  fi
fi

echo
if (( FAILED == 1 )); then
  echo "Diagnóstico: FALHOU — corrija os itens [FALHA] acima antes de subir o ambiente."
  exit 1
elif (( WARNED == 1 )); then
  echo "Diagnóstico: OK com avisos."
  exit 0
else
  echo "Diagnóstico: OK."
  exit 0
fi
