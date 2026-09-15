<#
.SYNOPSIS
    Inicia um broker Apache Kafka nativo em modo KRaft (sem ZooKeeper e sem Docker/Podman).

.DESCRIPTION
    Se o Apache Kafka ainda não estiver presente em .kafka/, baixa automaticamente a distribuição
    oficial da Apache, formata o cluster com o CLUSTER_ID do projeto e inicia o broker na porta 9092.
    Em seguida, cria todos os tópicos do catálogo oficial (contracts/events/topics.yaml).

.PARAMETER Port
    Porta do broker Kafka (padrão: 9092).

.PARAMETER Mode
    "Background" (grava logs em logs/kafka.log) ou "NewWindow" (abre em nova janela do console).

.EXAMPLE
    .\scripts\start-kafka-local.ps1
    .\scripts\start-kafka-local.ps1 -Mode NewWindow
#>
param(
    [int]$Port = 9092,
    [int]$ControllerPort = 9093,
    [string]$ClusterId = "MkU3OEVBNTcwNTJENDM2Qk",
    [ValidateSet("Background", "NewWindow")]
    [string]$Mode = "Background"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path "$PSScriptRoot/.."
$KafkaBaseDir = Join-Path $RepoRoot ".kafka"
$KafkaVersion = "3.8.0"
$KafkaScalaVersion = "2.13"
$KafkaDirName = "kafka_${KafkaScalaVersion}-${KafkaVersion}"
$KafkaHome = Join-Path $KafkaBaseDir $KafkaDirName
$LogDir = Join-Path $RepoRoot "logs"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}

function Test-PortOpen {
    param([int]$CheckPort)
    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $ar = $client.BeginConnect("127.0.0.1", $CheckPort, $null, $null)
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

if (Test-PortOpen -CheckPort $Port) {
    Write-Host "==> Kafka já está respondendo na porta $Port!" -ForegroundColor Green
    exit 0
}

# 1. Download do binário se não existir
if (-not (Test-Path $KafkaHome)) {
    Write-Host "==> Apache Kafka não encontrado em .kafka/. Baixando distribuição oficial..." -ForegroundColor Yellow
    if (-not (Test-Path $KafkaBaseDir)) {
        New-Item -ItemType Directory -Path $KafkaBaseDir | Out-Null
    }

    $tarName = "${KafkaDirName}.tgz"
    $tarPath = Join-Path $KafkaBaseDir $tarName
    $downloadUrl = "https://archive.apache.org/dist/kafka/${KafkaVersion}/${tarName}"

    if (-not (Test-Path $tarPath)) {
        Write-Host "    Baixando $downloadUrl..." -ForegroundColor Cyan
        Invoke-WebRequest -Uri $downloadUrl -OutFile $tarPath
    }

    Write-Host "    Extraindo arquivos com tar..." -ForegroundColor Cyan
    & tar -xzf $tarPath -C $KafkaBaseDir
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha ao extrair o arquivo tar do Kafka."
        exit 1
    }
    Remove-Item $tarPath -Force -ErrorAction SilentlyContinue
    Write-Host "    Download e extração concluídos." -ForegroundColor Green
}

# 2. Gerar arquivo server.properties KRaft
$configPath = Join-Path $KafkaBaseDir "kraft-server.properties"
$dataDir = Join-Path $KafkaBaseDir "kafka-logs"
$dataDirEscaped = $dataDir.Replace('\', '/')

$configContent = @"
process.roles=broker,controller
node.id=1
controller.quorum.voters=1@localhost:${ControllerPort}
listeners=PLAINTEXT://:${Port},CONTROLLER://:${ControllerPort}
inter.broker.listener.name=PLAINTEXT
advertised.listeners=PLAINTEXT://localhost:${Port}
controller.listener.names=CONTROLLER
listener.security.protocol.map=CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT
num.network.threads=3
num.io.threads=8
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600
log.dirs=${dataDirEscaped}
num.partitions=6
num.recovery.threads.per.data.dir=1
offsets.topic.replication.factor=1
transaction.state.log.replication.factor=1
transaction.state.log.min.isr=1
log.retention.hours=168
log.retention.check.interval.ms=300000
auto.create.topics.enable=false
"@
Set-Content -Path $configPath -Value $configContent -Encoding ASCII

# 3. Formatar storage KRaft se ainda não formatado
$metaProperties = Join-Path $dataDir "meta.properties"
$binWindows = Join-Path $KafkaHome "bin\windows"
$storageBat = Join-Path $binWindows "kafka-storage.bat"
$serverStartBat = Join-Path $binWindows "kafka-server-start.bat"
$topicsBat = Join-Path $binWindows "kafka-topics.bat"

if (-not (Test-Path $metaProperties)) {
    Write-Host "==> Formatando armazenamento KRaft (Cluster ID: $ClusterId)..." -ForegroundColor Yellow
    & "$storageBat" format -t $ClusterId -c "$configPath" | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "Falha ao formatar armazenamento KRaft do Kafka."
        exit 1
    }
    Write-Host "    Armazenamento formatado com sucesso." -ForegroundColor Green
}

# 4. Iniciar broker Kafka
Write-Host "==> Iniciando Apache Kafka na porta $Port ($Mode)..." -ForegroundColor Yellow
$kafkaLog = Join-Path $LogDir "kafka.log"
$kafkaErr = Join-Path $LogDir "kafka.err.log"

if ($Mode -eq "Background") {
    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = "/c `"$serverStartBat`" `"$configPath`""
    $psi.WorkingDirectory = $KafkaHome
    $psi.UseShellExecute = $false
    $psi.RedirectStandardOutput = $true
    $psi.RedirectStandardError = $true
    $psi.CreateNoWindow = $true

    $proc = New-Object System.Diagnostics.Process
    $proc.StartInfo = $psi
    $proc.Start() | Out-Null

    $outStream = [System.IO.StreamWriter]::new($kafkaLog, $true, [System.Text.Encoding]::UTF8)
    $errStream = [System.IO.StreamWriter]::new($kafkaErr, $true, [System.Text.Encoding]::UTF8)
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

    # Salva PID no .local-processes.json se existir
    $procFile = Join-Path $RepoRoot ".local-processes.json"
    $list = @()
    if (Test-Path $procFile) {
        try { $list = @(Get-Content $procFile -Raw | ConvertFrom-Json) } catch {}
    }
    $list += [PSCustomObject]@{
        Name      = "kafka"
        Port      = $Port
        Pid       = $proc.Id
        HealthUrl = "tcp://localhost:$Port"
        LogFile   = "logs/kafka.log"
    }
    $list | ConvertTo-Json -Depth 3 | Set-Content -Path $procFile -Encoding UTF8
} else {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'Apache Kafka (Porta $Port)'; & '$serverStartBat' '$configPath'"
}

# 5. Aguardar Kafka aceitar conexões TCP
Write-Host "    Aguardando Kafka responder na porta $Port..." -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 30; $i++) {
    Start-Sleep -Seconds 1
    if (Test-PortOpen -CheckPort $Port) {
        $ready = $true
        break
    }
}

if (-not $ready) {
    Write-Error "Kafka não iniciou dentro de 30 segundos. Verifique logs/kafka.err.log"
    exit 1
}
Write-Host "    Kafka está UP na porta $Port!" -ForegroundColor Green

# 6. Criar tópicos do catálogo oficial
Write-Host "==> Criando tópicos do catálogo oficial (contracts/events/topics.yaml)..." -ForegroundColor Yellow
$catalogTopics = @(
    "marketdata.rotina.v1",
    "marketdata.prioritaria.v1",
    "marketdata.massa.v1",
    "marketdata.normalized.v1",
    "curve.published.v1",
    "marketdata.rotina.v1.curve-processor-rotina.dlq",
    "marketdata.prioritaria.v1.curve-processor-prioritaria.dlq",
    "marketdata.massa.v1.curve-processor-massa.dlq",
    "marketdata.normalized.v1.curve-orchestrator-normalized.dlq",
    "curve.published.v1.curve-orchestrator-published.dlq"
)

foreach ($top in $catalogTopics) {
    $retention = if ($top -match "curve\.published|dlq") { "2592000000" } else { "604800000" }
    & "$topicsBat" --bootstrap-server "localhost:$Port" --create --if-not-exists --topic $top --partitions 6 --replication-factor 1 --config "retention.ms=$retention" 2>&1 | Out-Null
    Write-Host "  [+] Tópico $top verificado/criado." -ForegroundColor DarkGray
}

Write-Host "==> Todos os tópicos do catálogo foram configurados no Kafka local!" -ForegroundColor Green
