# ==============================================================================
# Derruba o ambiente local. Equivalente Windows de down.sh.
#
# Uso: deploy/podman/down.ps1 [-Lite] [-Volumes]
#   -Volumes  remove também os volumes nomeados (banco e tópicos são apagados)
# ==============================================================================
param(
    [switch]$Lite,
    [switch]$Volumes
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$ComposeFile = if ($Lite) { "compose.lite.yaml" } else { "compose.yaml" }

Write-Host "==> Derrubando ambiente ($ComposeFile), volumes removidos: $Volumes"

if ($Volumes) {
    podman compose -f $ComposeFile down -v
} else {
    podman compose -f $ComposeFile down
}

Write-Host "==> Ambiente derrubado"
