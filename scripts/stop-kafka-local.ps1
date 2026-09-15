<#
.SYNOPSIS
    Finaliza o broker Apache Kafka nativo em execução local.
#>
param(
    [int]$Port = 9092,
    [switch]$Quiet
)

$targetPorts = @($Port, 9093)
foreach ($p in $targetPorts) {
    try {
        $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue
        foreach ($c in $conns) {
            $orphanPid = $c.OwningProcess
            if ($orphanPid -and $orphanPid -ne 0 -and $orphanPid -ne $PID) {
                try {
                    $proc = Get-Process -Id $orphanPid -ErrorAction SilentlyContinue
                    Stop-Process -Id $orphanPid -Force -ErrorAction SilentlyContinue
                    if (-not $Quiet) {
                        Write-Host "  [-] Processo Kafka (PID $orphanPid na porta $p) finalizado." -ForegroundColor Green
                    }
                } catch {}
            }
        }
    } catch {}
}

if (-not $Quiet) {
    Write-Host "==> Apache Kafka local finalizado." -ForegroundColor Green
}
