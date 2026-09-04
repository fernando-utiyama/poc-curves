# ==============================================================================
# Sobe o ambiente local: infraestrutura -> espera ativa por prontidão real de
# cada serviço -> criação do banco -> bootstrap de tópicos -> migração Flyway.
# Equivalente Windows de up.sh; mesma sequência, mesmos critérios de prontidão.
#
# Uso: deploy/podman/up.ps1 [-Lite]
# ==============================================================================
param(
    [switch]$Lite
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$Profile_ = if ($Lite) { "lite" } else { "full" }
$ComposeFile = if ($Lite) { "compose.lite.yaml" } else { "compose.yaml" }
$SaPassword = if ($env:MSSQL_SA_PASSWORD) { $env:MSSQL_SA_PASSWORD } else { "CurvasP0c!Local" }

Write-Host "==> Perfil: $Profile_ ($ComposeFile)"

function Wait-For {
    param(
        [string]$Description,
        [int]$TimeoutSeconds,
        [scriptblock]$Check
    )
    Write-Host "==> Aguardando: $Description"
    $waited = 0
    while ($true) {
        try {
            & $Check | Out-Null
            if ($LASTEXITCODE -eq 0 -or $null -eq $LASTEXITCODE) {
                Write-Host "    ok (${waited}s)"
                return
            }
        } catch {}
        Start-Sleep -Seconds 3
        $waited += 3
        if ($waited -ge $TimeoutSeconds) {
            Write-Error "tempo limite (${TimeoutSeconds}s) excedido aguardando: $Description"
        }
    }
}

Write-Host "==> Subindo infraestrutura em background"
podman compose -f $ComposeFile up -d kafka sqlserver
if ($Profile_ -eq "full") {
    podman compose -f $ComposeFile up -d redis keycloak
}

Wait-For "SQL Server pronto" 180 {
    podman exec curvas-sqlserver /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P $SaPassword -C -N -Q "SELECT 1"
}

Wait-For "Kafka pronto" 120 {
    podman exec curvas-kafka /opt/kafka/bin/kafka-broker-api-versions.sh --bootstrap-server localhost:9092
}

if ($Profile_ -eq "full") {
    Wait-For "Redis pronto" 60 {
        podman exec curvas-redis redis-cli ping
    }
    Wait-For "Keycloak pronto" 120 {
        curl.exe -sf http://localhost:9000/health/ready
    }
}

Write-Host "==> Criando banco curvasdb (se necessário)"
podman compose -f $ComposeFile run --rm sqlserver-init-db
if ($LASTEXITCODE -ne 0) { Write-Error "falha ao criar o banco curvasdb" }
Write-Host "    ok"

Write-Host "==> Aplicando migrações Flyway"
podman compose -f $ComposeFile run --rm flyway-migrate
if ($LASTEXITCODE -ne 0) { Write-Error "migração Flyway falhou" }
Write-Host "    ok"

Write-Host "==> Criando tópicos do catálogo Kafka"
podman compose -f $ComposeFile run --rm kafka-topics-bootstrap
if ($LASTEXITCODE -ne 0) { Write-Error "bootstrap de tópicos falhou" }
Write-Host "    ok"

Write-Host "==> Ambiente local pronto (perfil: $Profile_)"
