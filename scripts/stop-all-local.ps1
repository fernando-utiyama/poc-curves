<#
.SYNOPSIS
    Finaliza todas as aplicações da Plataforma de Curvas rodando nativamente na máquina.

.DESCRIPTION
    Lê o arquivo .local-processes.json e encerra os processos por PID.
    Também verifica e libera as portas padrão da aplicação (8080, 8081, 8082, 8083, 8084, 8091, 4200, 9092, 9093, 11433)
    garantindo que nenhum processo órfão permaneça aberto.

.PARAMETER Quiet
    Executa em modo silencioso sem imprimir cabeçalhos.

.EXAMPLE
    .\scripts\stop-all-local.ps1
#>
param(
    [switch]$Quiet
)

$RepoRoot = Resolve-Path "$PSScriptRoot/.."
$processFile = Join-Path $RepoRoot ".local-processes.json"
$targetPorts = @(8080, 8081, 8082, 8083, 8084, 8091, 4200, 9092, 9093, 11433)

if (-not $Quiet) {
    Write-Host "==> Finalizando aplicações locais..." -ForegroundColor Yellow
}

# 1. Finalizar processos registrados em .local-processes.json
if (Test-Path $processFile) {
    try {
        $json = Get-Content $processFile -Raw | ConvertFrom-Json
        foreach ($item in $json) {
            $pidToStop = $item.Pid
            $name = $item.Name
            if ($pidToStop) {
                try {
                    $proc = Get-Process -Id $pidToStop -ErrorAction SilentlyContinue
                    if ($proc) {
                        Stop-Process -Id $pidToStop -Force -ErrorAction SilentlyContinue
                        if (-not $Quiet) {
                            Write-Host "  [-] $name (PID $pidToStop) finalizado." -ForegroundColor Green
                        }
                    }
                } catch {}
            }
        }
    } catch {}
    Remove-Item $processFile -Force -ErrorAction SilentlyContinue
}

# 2. Varredura e liberação de portas caso algum processo órfão tenha sobrado
foreach ($port in $targetPorts) {
    try {
        $connections = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
        foreach ($conn in $connections) {
            $orphanPid = $conn.OwningProcess
            if ($orphanPid -and $orphanPid -ne 0 -and $orphanPid -ne $PID) {
                try {
                    $proc = Get-Process -Id $orphanPid -ErrorAction SilentlyContinue
                    if ($proc) {
                        Stop-Process -Id $orphanPid -Force -ErrorAction SilentlyContinue
                        if (-not $Quiet) {
                            Write-Host "  [-] Processo órfão '$($proc.ProcessName)' (PID $orphanPid na porta $port) encerrado." -ForegroundColor Yellow
                        }
                    }
                } catch {}
            }
        }
    } catch {}
}

if (-not $Quiet) {
    Write-Host "==> Todas as aplicações foram encerradas e portas liberadas." -ForegroundColor Green
}
