<#
.SYNOPSIS
    Inicia um servidor H2 TCP nativo (sem containers e sem SQL Server).

.DESCRIPTION
    Executa o servidor H2 TCP na porta 11433 com banco de dados "curvasdb"
    compartilhado entre todos os microsserviços da Plataforma de Curvas.
    Aplica o esquema inicial db/h2/schema.sql caso o banco esteja vazio.

.PARAMETER Port
    Porta TCP do servidor H2 (padrão: 11433).

.PARAMETER Mode
    "Background" (padrão) ou "NewWindow".

.EXAMPLE
    .\scripts\start-h2-local.ps1
#>
param(
    [int]$Port = 11433,
    [ValidateSet("Background", "NewWindow")]
    [string]$Mode = "Background"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path "$PSScriptRoot/.."
$H2BaseDir = Join-Path $RepoRoot ".h2"
$LogDir = Join-Path $RepoRoot "logs"
$H2Version = "2.3.232"
$H2Jar = Join-Path $H2BaseDir "h2-${H2Version}.jar"

if (-not (Test-Path $LogDir)) {
    New-Item -ItemType Directory -Path $LogDir | Out-Null
}
if (-not (Test-Path $H2BaseDir)) {
    New-Item -ItemType Directory -Path $H2BaseDir | Out-Null
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
    Write-Host "==> H2 Database Server já está respondendo na porta $Port!" -ForegroundColor Green
    exit 0
}

# 1. Download do JAR do H2 se necessário
if (-not (Test-Path $H2Jar)) {
    $m2Jar = Join-Path $HOME ".m2/repository/com/h2database/h2/$H2Version/h2-${H2Version}.jar"
    if (Test-Path $m2Jar) {
        Copy-Item -Path $m2Jar -Destination $H2Jar
    } else {
        Write-Host "==> Baixando com.h2database:h2:$H2Version..." -ForegroundColor Yellow
        $downloadUrl = "https://repo1.maven.org/maven2/com/h2database/h2/$H2Version/h2-${H2Version}.jar"
        Invoke-WebRequest -Uri $downloadUrl -OutFile $H2Jar
        Write-Host "    Download concluído." -ForegroundColor Green
    }
}

# 2. Iniciar o servidor H2 TCP
Write-Host "==> Iniciando Servidor H2 TCP na porta $Port ($Mode)..." -ForegroundColor Yellow
$h2Log = Join-Path $LogDir "h2.log"
$h2Err = Join-Path $LogDir "h2.err.log"

$h2Args = @(
    "-cp", "`"$H2Jar`"",
    "org.h2.tools.Server",
    "-tcp",
    "-tcpAllowOthers",
    "-tcpPort", "$Port",
    "-baseDir", "`"$H2BaseDir`"",
    "-ifNotExists"
)

if ($Mode -eq "Background") {
    $proc = Start-Process java `
        -ArgumentList $h2Args `
        -WorkingDirectory $H2BaseDir `
        -RedirectStandardOutput $h2Log `
        -RedirectStandardError $h2Err `
        -NoNewWindow `
        -PassThru

    # Salva PID no .local-processes.json
    $procFile = Join-Path $RepoRoot ".local-processes.json"
    $list = @()
    if (Test-Path $procFile) {
        try { $list = @(Get-Content $procFile -Raw | ConvertFrom-Json) } catch {}
    }
    $list += [PSCustomObject]@{
        Name      = "h2-database"
        Port      = $Port
        Pid       = $proc.Id
        HealthUrl = "tcp://localhost:$Port"
        LogFile   = "logs/h2.log"
    }
    $list | ConvertTo-Json -Depth 3 | Set-Content -Path $procFile -Encoding UTF8
} else {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "`$host.UI.RawUI.WindowTitle = 'H2 TCP Server (Porta $Port)'; java $($h2Args -join ' ')"
}

# 3. Aguardar TCP port responder
Write-Host "    Aguardando H2 responder na porta $Port..." -ForegroundColor Cyan
$ready = $false
for ($i = 1; $i -le 20; $i++) {
    Start-Sleep -Seconds 1
    if (Test-PortOpen -CheckPort $Port) {
        $ready = $true
        break
    }
}

if (-not $ready) {
    Write-Error "Falha ao iniciar o H2 TCP Server dentro de 20 segundos. Verifique logs/h2.err.log"
    exit 1
}
Write-Host "    H2 TCP Server está UP na porta $Port!" -ForegroundColor Green

# 4. Executar schema inicial db/h2/schema.sql
$schemaScript = Join-Path $RepoRoot "db/h2/schema.sql"
if (Test-Path $schemaScript) {
    Write-Host "==> Aplicando esquema do banco curvasdb (db/h2/schema.sql)..." -ForegroundColor Yellow
    $jdbcUrl = "jdbc:h2:tcp://localhost:$Port/curvasdb;MODE=MSSQLServer;CASE_INSENSITIVE_IDENTIFIERS=TRUE"

    $runArgs = @(
        "-cp", "`"$H2Jar`"",
        "org.h2.tools.RunScript",
        "-url", $jdbcUrl,
        "-user", "sa",
        "-script", $schemaScript
    )
    $runProc = Start-Process java -ArgumentList $runArgs -NoNewWindow -Wait -PassThru
    if ($runProc.ExitCode -eq 0) {
        Write-Host "    Esquema H2 aplicado com sucesso." -ForegroundColor Green
    } else {
        Write-Host "    Aviso: RunScript executou com retorno $($runProc.ExitCode)." -ForegroundColor Yellow
    }
}
