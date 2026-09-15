<#
.SYNOPSIS
    Inicia todas as aplicações da Plataforma de Curvas nativamente na máquina local (sem Docker/Podman).

.DESCRIPTION
    Sobe os 5 microsserviços Spring Boot (curve-processor, curve-api, curve-engine,
    curve-orchestrator, curve-bff), o feeder Node.js (function-marketdata em modo HTTP) e
    o frontend Angular (curve-web-ui).
    Opcionalmente também inicia o Apache Kafka local nativo (em modo KRaft, sem containers)
    e aplica as migrações de banco Flyway.

.PARAMETER DbHost
    Host do SQL Server (padrão: localhost).

.PARAMETER DbPort
    Porta do SQL Server (padrão: 1433).

.PARAMETER DbName
    Nome do banco de dados (padrão: curvasdb).

.PARAMETER DbUser
    Usuário para sobrescrever as credenciais restritas do banco (opcional, ex.: sa).

.PARAMETER DbPassword
    Senha do usuário do banco (opcional).

.PARAMETER KafkaBootstrapServers
    Endereço do cluster Kafka (padrão: localhost:9092).

.PARAMETER WithKafka
    Inicia o broker Apache Kafka nativo em modo KRaft (sem ZooKeeper/containers) e cria os tópicos.

.PARAMETER RedisHost
    Host do Redis para cache de interpolação (padrão: localhost).

.PARAMETER RedisPort
    Porta do Redis (padrão: 6379).

.PARAMETER KeycloakUrl
    URL base do Keycloak OIDC (padrão: http://localhost:8180).

.PARAMETER Build
    Força o build do Maven e compilação do Node antes de subir.

.PARAMETER MigrateDb
    Aplica migrações Flyway locais no SQL Server antes de subir as aplicações.

.PARAMETER SkipUi
    Não sobe o frontend Angular (curve-web-ui).

.PARAMETER SkipFeeder
    Não sobe o feeder HTTP (function-marketdata).

.PARAMETER Mode
    Modo de execução: "Background" (padrão, grava logs em logs/) ou "NewWindows" (abre uma janela de terminal para cada serviço).

.EXAMPLE
    .\scripts\start-all-local.ps1 -WithKafka
    .\scripts\start-all-local.ps1 -WithKafka -Build
    .\scripts\start-all-local.ps1 -WithKafka -MigrateDb -Build
    .\scripts\start-all-local.ps1 -WithKafka -Mode NewWindows
#>
param(
    [string]$DbHost = "localhost",
    [int]$DbPort = 1433,
    [string]$DbName = "curvasdb",
    [string]$DbUser = "",
    [string]$DbPassword = "",
    [string]$KafkaBootstrapServers = "localhost:9092",
    [switch]$WithKafka,
    [string]$RedisHost = "localhost",
    [int]$RedisPort = 6379,
    [string]$KeycloakUrl = "http://localhost:8180",
    [string]$MaxMemoryPerService = "384m",
    [switch]$Build,
    [switch]$MigrateDb,
    [switch]$SkipUi,
    [switch]$SkipFeeder,
    [ValidateSet("Background", "NewWindows")]
    [string]$Mode = "Background"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path "$PSScriptRoot/.."
Set-Location -Path $RepoRoot

Write-Host "==================================================================" -ForegroundColor Cyan
Write-Host "  PLATAFORMA DE CURVAS - INICIALIZAÇÃO LOCAL (SEM DOCKER/PODMAN) " -ForegroundColor Cyan
Write-Host "==================================================================" -ForegroundColor Cyan

# Função auxiliar para verificar portas TCP
function Test-PortOpen {
    param([string]$TargetHost, [int]$TargetPort)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $client.BeginConnect($TargetHost, $TargetPort, $null, $null)
        if ($ar.AsyncWaitHandle.WaitOne(1000)) {
            $client.EndConnect($ar)
            return $true
        }
        return $false
    } catch {
        return $false
    } finally {
        $client.Close()
    }
}

# 1. Checagem de ferramentas
Write-Host "`n==> Verificando pré-requisitos no PATH..." -ForegroundColor Yellow
$missing = @()
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { $missing += "java (JDK 21+)" }
if (-not (Get-Command mvn -ErrorAction SilentlyContinue))  { $missing += "mvn (Apache Maven 3.9+)" }
if (-not (Get-Command node -ErrorAction SilentlyContinue)) { $missing += "node (Node.js 20+)" }
if (-not (Get-Command npm -ErrorAction SilentlyContinue))  { $missing += "npm" }

if ($missing.Count -gt 0) {
    Write-Error "Ferramentas ausentes no PATH: $($missing -join ', '). Instale e configure o PATH antes de prosseguir."
    exit 1
}
Write-Host "    Java, Maven, Node e NPM encontrados." -ForegroundColor Green

# 2. Inicializar Kafka nativo (se solicitado via -WithKafka)
if ($WithKafka) {
    Write-Host "`n==> Inicializando Apache Kafka nativo (KRaft)..." -ForegroundColor Yellow
    $kafkaMode = if ($Mode -eq "NewWindows") { "NewWindow" } else { "Background" }
    & "$PSScriptRoot/start-kafka-local.ps1" -Port 9092 -Mode $kafkaMode
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha ao inicializar o Kafka local."
        exit 1
    }
} else {
    # Diagnóstico de conectividade ao Kafka
    $kafkaHost = ($KafkaBootstrapServers -split ':')[0]
    $kafkaPort = [int](($KafkaBootstrapServers -split ':')[1])
    if (-not (Test-PortOpen -TargetHost $kafkaHost -TargetPort $kafkaPort)) {
        Write-Warning "Kafka em $KafkaBootstrapServers não está respondendo. Dica: passe o parâmetro -WithKafka para que o script baixe e suba o Kafka KRaft nativamente."
    }
}

# Diagnóstico de conectividade ao SQL Server
if (-not (Test-PortOpen -TargetHost $DbHost -TargetPort $DbPort)) {
    Write-Warning "SQL Server em ${DbHost}:${DbPort} não está respondendo. Certifique-se de que a instância local do banco esteja ativa."
}

# 3. Migrações de banco (opcional via flag -MigrateDb)
if ($MigrateDb) {
    Write-Host "`n==> Aplicando migrações de banco de dados..." -ForegroundColor Yellow
    $migrateParams = @{
        Server = "$DbHost,$DbPort"
        Database = $DbName
    }
    if ($DbUser) { $migrateParams.User = $DbUser }
    if ($DbPassword) { $migrateParams.Password = $DbPassword }
    & "$PSScriptRoot/migrate-local.ps1" @migrateParams
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha na execução das migrações do banco de dados."
        exit 1
    }
}

# 4. Compilação (se solicitado ou se JARs/dist ausentes)
$javaModules = @("curve-processor", "curve-engine", "curve-api", "curve-bff", "curve-orchestrator")
$needMavenBuild = $Build
if (-not $needMavenBuild) {
    foreach ($m in $javaModules) {
        $jar = Get-ChildItem -Path "services/$m/target" -Filter "$m-*.jar" -Exclude "*plain.jar","*sources.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
        if (-not $jar) {
            $needMavenBuild = $true
            break
        }
    }
}

if ($needMavenBuild) {
    Write-Host "`n==> Compilando módulos Java (Maven)..." -ForegroundColor Yellow
    & mvn clean package -DskipTests
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha na compilação dos projetos Maven."
        exit 1
    }
    Write-Host "    Build Maven concluído com sucesso." -ForegroundColor Green
}

# Compilação do function-marketdata
$feederDist = "services/function-marketdata/dist/main-http.js"
if ($Build -or (-not (Test-Path $feederDist))) {
    Write-Host "`n==> Sincronizando contratos e compilando function-marketdata..." -ForegroundColor Yellow
    Push-Location "services/function-marketdata"
    try {
        if (-not (Test-Path "node_modules")) {
            & npm install
        }
        & npm run sync-contracts
        & npx tsc -p tsconfig.json --outDir dist --noEmit false
        if ($LASTEXITCODE -ne 0) {
            Write-Error "Falha na compilação do function-marketdata."
            exit 1
        }
    } finally {
        Pop-Location
    }
    Write-Host "    function-marketdata compilado com sucesso." -ForegroundColor Green
}

# Verificação do curve-web-ui
if (-not $SkipUi -and (-not (Test-Path "web/curve-web-ui/node_modules"))) {
    Write-Host "`n==> Instalando dependências do curve-web-ui..." -ForegroundColor Yellow
    Push-Location "web/curve-web-ui"
    try {
        & npm install
    } finally {
        Pop-Location
    }
}

# 5. Parar execuções anteriores de aplicações registradas (preservando kafka se já estava)
$processFile = Join-Path $RepoRoot ".local-processes.json"
if (Test-Path $processFile) {
    Write-Host "`n==> Finalizando instâncias locais anteriores..." -ForegroundColor Yellow
    & "$PSScriptRoot/stop-all-local.ps1" -Quiet
}

# 6. Configurar diretório de logs
$logDir = Join-Path $RepoRoot "logs"
if (-not (Test-Path $logDir)) {
    New-Item -ItemType Directory -Path $logDir | Out-Null
}

$datasourceUrl = "jdbc:sqlserver://${DbHost}:${DbPort};databaseName=${DbName};trustServerCertificate=true;encrypt=true"
$jwtUri = "${KeycloakUrl}/realms/curvas/protocol/openid-connect/certs"

# Base de propriedades comuns do Spring Boot
function Get-SpringCommonArgs {
    param([string]$AppName)
    $args = @(
        "-Xms128m",
        "-Xmx$MaxMemoryPerService",
        "-Dspring.datasource.url=$datasourceUrl",
        "-Dspring.kafka.bootstrap-servers=$KafkaBootstrapServers",
        "-Dspring.security.oauth2.resourceserver.jwt.jwk-set-uri=$jwtUri"
    )
    if ($DbUser) {
        $args += "-Dspring.datasource.username=$DbUser"
        $args += "-Dspring.datasource.password=$DbPassword"
    }
    return $args
}

$runningProcesses = @()

# Função para iniciar processo
function Start-ServiceProcess {
    param(
        [string]$Name,
        [int]$Port,
        [string]$WorkDir,
        [string]$Executable,
        [string[]]$Arguments,
        [hashtable]$EnvVars = @{},
        [string]$HealthUrl
    )

    Write-Host "  [+] Iniciando $Name (porta $Port)..." -ForegroundColor Cyan
    $stdoutLog = Join-Path $logDir "$Name.log"
    $stderrLog = Join-Path $logDir "$Name.err.log"

    if (Test-Path $stdoutLog) { Remove-Item $stdoutLog -Force }
    if (Test-Path $stderrLog) { Remove-Item $stderrLog -Force }

    $startDir = Join-Path $RepoRoot $WorkDir

    if ($Mode -eq "Background") {
        $psi = New-Object System.Diagnostics.ProcessStartInfo
        $psi.FileName = $Executable
        $psi.Arguments = ($Arguments -join ' ')
        $psi.WorkingDirectory = $startDir
        $psi.UseShellExecute = $false
        $psi.RedirectStandardOutput = $true
        $psi.RedirectStandardError = $true
        $psi.CreateNoWindow = $true

        foreach ($k in $EnvVars.Keys) {
            $psi.EnvironmentVariables[$k] = [string]$EnvVars[$k]
        }

        $proc = New-Object System.Diagnostics.Process
        $proc.StartInfo = $psi
        $proc.Start() | Out-Null

        $outStream = [System.IO.StreamWriter]::new($stdoutLog, $true, [System.Text.Encoding]::UTF8)
        $errStream = [System.IO.StreamWriter]::new($stderrLog, $true, [System.Text.Encoding]::UTF8)
        $outStream.AutoFlush = $true
        $errStream.AutoFlush = $true

        $proc.add_OutputDataReceived({
            if ($null -ne $_.Data) { $outStream.WriteLine($_.Data) }
        })
        $proc.add_ErrorDataReceived({
            if ($null -ne $_.Data) { $errStream.WriteLine($_.Data) }
        })
        $proc.BeginOutputReadLine()
        $proc.BeginErrorReadLine()

        $script:runningProcesses += [PSCustomObject]@{
            Name      = $Name
            Port      = $Port
            Pid       = $proc.Id
            HealthUrl = $HealthUrl
            LogFile   = "logs/$Name.log"
        }
    } else {
        $envSetup = ""
        foreach ($k in $EnvVars.Keys) {
            $envSetup += "`$env:$k = '$($EnvVars[$k])'; "
        }
        $cmdLine = "$envSetup & '$Executable' $($Arguments -join ' ')"
        $windowTitle = "Curvas - $Name (Porta $Port)"

        $proc = Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = '$windowTitle'; Set-Location '$startDir'; $cmdLine" -PassThru

        $script:runningProcesses += [PSCustomObject]@{
            Name      = $Name
            Port      = $Port
            Pid       = $proc.Id
            HealthUrl = $HealthUrl
            LogFile   = "Janela externa"
        }
    }
}

Write-Host "`n==> Iniciando aplicações ($Mode)..." -ForegroundColor Yellow

# 6.1 Feeder: function-marketdata (Node.js)
if (-not $SkipFeeder) {
    Start-ServiceProcess `
        -Name "function-marketdata" `
        -Port 8091 `
        -WorkDir "services/function-marketdata" `
        -Executable "node" `
        -Arguments @("dist/main-http.js") `
        -EnvVars @{
            "PORT" = "8091"
            "KAFKA_BOOTSTRAP_SERVERS" = $KafkaBootstrapServers
        } `
        -HealthUrl "http://localhost:8091/health"
}

# 6.2 curve-processor (Spring Boot)
$procJar = (Get-ChildItem -Path "services/curve-processor/target" -Filter "curve-processor-*.jar" -Exclude "*plain.jar" | Select-Object -First 1).FullName
Start-ServiceProcess `
    -Name "curve-processor" `
    -Port 8081 `
    -WorkDir "services/curve-processor" `
    -Executable "java" `
    -Arguments ((Get-SpringCommonArgs "curve-processor") + @("-jar", "`"$procJar`"")) `
    -HealthUrl "http://localhost:8081/actuator/health"

# 6.3 curve-api (Spring Boot)
$apiJar = (Get-ChildItem -Path "services/curve-api/target" -Filter "curve-api-*.jar" -Exclude "*plain.jar" | Select-Object -First 1).FullName
Start-ServiceProcess `
    -Name "curve-api" `
    -Port 8082 `
    -WorkDir "services/curve-api" `
    -Executable "java" `
    -Arguments ((Get-SpringCommonArgs "curve-api") + @("-jar", "`"$apiJar`"")) `
    -HealthUrl "http://localhost:8082/actuator/health"

# 6.4 curve-engine (Spring Boot)
$engineJar = (Get-ChildItem -Path "services/curve-engine/target" -Filter "curve-engine-*.jar" -Exclude "*plain.jar" | Select-Object -First 1).FullName
$engineArgs = (Get-SpringCommonArgs "curve-engine") + @(
    "-Dspring.data.redis.host=$RedisHost",
    "-Dspring.data.redis.port=$RedisPort",
    "-Dservices.curve-orchestrator.url=http://localhost:8084",
    "-jar", "`"$engineJar`""
)
Start-ServiceProcess `
    -Name "curve-engine" `
    -Port 8083 `
    -WorkDir "services/curve-engine" `
    -Executable "java" `
    -Arguments $engineArgs `
    -HealthUrl "http://localhost:8083/actuator/health"

# 6.5 curve-orchestrator (Spring Boot)
$orchJar = (Get-ChildItem -Path "services/curve-orchestrator/target" -Filter "curve-orchestrator-*.jar" -Exclude "*plain.jar" | Select-Object -First 1).FullName
$orchArgs = (Get-SpringCommonArgs "curve-orchestrator") + @(
    "-Dservices.function-marketdata.url=http://localhost:8091",
    "-Dservices.curve-processor.url=http://localhost:8081",
    "-Dservices.curve-engine.url=http://localhost:8083",
    "-jar", "`"$orchJar`""
)
Start-ServiceProcess `
    -Name "curve-orchestrator" `
    -Port 8084 `
    -WorkDir "services/curve-orchestrator" `
    -Executable "java" `
    -Arguments $orchArgs `
    -HealthUrl "http://localhost:8084/actuator/health"

# 6.6 curve-bff (Spring Boot)
$bffJar = (Get-ChildItem -Path "services/curve-bff/target" -Filter "curve-bff-*.jar" -Exclude "*plain.jar" | Select-Object -First 1).FullName
$bffArgs = (Get-SpringCommonArgs "curve-bff") + @(
    "-Dcors.allowed-origins=http://localhost:4200",
    "-Dservices.curve-api.url=http://localhost:8082",
    "-Dservices.curve-engine.url=http://localhost:8083",
    "-Dservices.curve-orchestrator.url=http://localhost:8084",
    "-jar", "`"$bffJar`""
)
Start-ServiceProcess `
    -Name "curve-bff" `
    -Port 8080 `
    -WorkDir "services/curve-bff" `
    -Executable "java" `
    -Arguments $bffArgs `
    -HealthUrl "http://localhost:8080/actuator/health"

# 6.7 curve-web-ui (Angular)
if (-not $SkipUi) {
    $npxCmd = if ($IsWindows -or $env:OS -eq "Windows_NT") { "npx.cmd" } else { "npx" }
    Start-ServiceProcess `
        -Name "curve-web-ui" `
        -Port 4200 `
        -WorkDir "web/curve-web-ui" `
        -Executable $npxCmd `
        -Arguments @("ng", "serve", "--port", "4200", "--proxy-config", "proxy.conf.json") `
        -HealthUrl "http://localhost:4200"
}

# Salvar processos ativos
$runningProcesses | ConvertTo-Json -Depth 3 | Set-Content -Path $processFile -Encoding UTF8

Write-Host "`n==> Aguardando prontidão dos serviços (até 45s)..." -ForegroundColor Yellow
$maxAttempts = 15
for ($attempt = 1; $attempt -le $maxAttempts; $attempt++) {
    Start-Sleep -Seconds 3
    $pending = 0
    foreach ($p in $runningProcesses) {
        try {
            $resp = Invoke-WebRequest -Uri $p.HealthUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
            if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
                # OK
            } else {
                $pending++
            }
        } catch {
            $pending++
        }
    }
    if ($pending -eq 0) { break }
}

Write-Host "`n==================================================================" -ForegroundColor Green
Write-Host "                       STATUS DOS SERVIÇOS                       " -ForegroundColor Green
Write-Host "==================================================================" -ForegroundColor Green

$results = foreach ($p in $runningProcesses) {
    $status = "OFFLINE"
    try {
        $resp = Invoke-WebRequest -Uri $p.HealthUrl -UseBasicParsing -TimeoutSec 2 -ErrorAction SilentlyContinue
        if ($resp.StatusCode -ge 200 -and $resp.StatusCode -lt 400) {
            $status = "UP"
        }
    } catch {}

    [PSCustomObject]@{
        "Serviço"    = $p.Name
        "Porta"      = $p.Port
        "PID"        = $p.Pid
        "Status"     = $status
        "Endpoint"   = $p.HealthUrl
        "Log"        = $p.LogFile
    }
}

$results | Format-Table -AutoSize

Write-Host "`n[DICA] Para encerrar todos os serviços locais:" -ForegroundColor Cyan
Write-Host "  .\scripts\stop-all-local.ps1`n" -ForegroundColor White

Write-Host "[DICA] Logs em tempo real de um serviço:" -ForegroundColor Cyan
Write-Host "  Get-Content logs\curve-bff.log -Wait`n" -ForegroundColor White
