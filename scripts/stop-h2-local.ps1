<#
.SYNOPSIS
    Finaliza o servidor H2 TCP em execução local.
#>
param(
    [int]$Port = 11433,
    [switch]$Quiet
)

try {
    $conns = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $orphanPid = $c.OwningProcess
        if ($orphanPid -and $orphanPid -ne 0 -and $orphanPid -ne $PID) {
            try {
                Stop-Process -Id $orphanPid -Force -ErrorAction SilentlyContinue
                if (-not $Quiet) {
                    Write-Host "  [-] Servidor H2 (PID $orphanPid na porta $Port) finalizado." -ForegroundColor Green
                }
            } catch {}
        }
    }
} catch {}

if (-not $Quiet) {
    Write-Host "==> Servidor H2 local finalizado." -ForegroundColor Green
}
