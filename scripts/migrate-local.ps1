<#
.SYNOPSIS
    Aplica migrações Flyway diretamente contra o SQL Server local sem depender de Docker ou Podman.

.DESCRIPTION
    Executa os scripts em db/migration/ (V1 a V20) ordenados numericamente usando sqlcmd.
    Mantém histórico na tabela flyway_schema_history para garantir idempotência e
    compatibilidade com Flyway oficial.

.EXAMPLE
    .\scripts\migrate-local.ps1
    .\scripts\migrate-local.ps1 -Server "localhost,1433" -Database "curvasdb" -Password "CurvasP0c!Local"
    .\scripts\migrate-local.ps1 -UseWindowsAuth
#>
param(
    [string]$Server = "localhost,1433",
    [string]$Database = "curvasdb",
    [string]$User = "sa",
    [string]$Password = $(if ($env:MSSQL_SA_PASSWORD) { $env:MSSQL_SA_PASSWORD } else { "CurvasP0c!Local" }),
    [switch]$UseWindowsAuth,
    [string]$MigrationDir = "$PSScriptRoot/../db/migration"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command sqlcmd -ErrorAction SilentlyContinue)) {
    Write-Error "sqlcmd não foi encontrado no PATH. Instale o Microsoft Command Line Utilities para SQL Server."
    exit 1
}

$authArgs = if ($UseWindowsAuth) {
    @("-E")
} else {
    @("-U", $User, "-P", $Password)
}

function Invoke-SqlCmdQuery {
    param(
        [string]$Query,
        [string]$Db = "master"
    )
    $args = @("-S", $Server, "-d", $Db, "-C", "-b", "-Q", $Query) + $authArgs
    $output = & sqlcmd $args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Erro executando query sqlcmd: $output"
    }
    return $output
}

function Invoke-SqlCmdFile {
    param(
        [string]$FilePath,
        [string]$Db
    )
    $args = @("-S", $Server, "-d", $Db, "-C", "-b", "-i", $FilePath) + $authArgs
    $output = & sqlcmd $args 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "Erro executando script SQL ($FilePath): $output"
    }
    return $output
}

Write-Host "==> Verificando conexão com SQL Server em $Server..." -ForegroundColor Cyan
try {
    Invoke-SqlCmdQuery "SELECT 1" "master" | Out-Null
    Write-Host "    Conexão estabelecida com sucesso." -ForegroundColor Green
} catch {
    Write-Error "Falha ao conectar no SQL Server ($Server): $_"
    exit 1
}

Write-Host "==> Verificando existência do banco $Database..." -ForegroundColor Cyan
Invoke-SqlCmdQuery "IF DB_ID('$Database') IS NULL CREATE DATABASE [$Database];" "master" | Out-Null

Write-Host "==> Verificando tabela flyway_schema_history em $Database..." -ForegroundColor Cyan
$initFlywayTable = @"
IF OBJECT_ID('dbo.flyway_schema_history', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.flyway_schema_history (
        installed_rank INT NOT NULL,
        version NVARCHAR(50),
        description NVARCHAR(200) NOT NULL,
        type NVARCHAR(20) NOT NULL,
        script NVARCHAR(1000) NOT NULL,
        checksum INT NULL,
        installed_by NVARCHAR(100) NOT NULL,
        installed_on DATETIME NOT NULL DEFAULT GETDATE(),
        execution_time INT NOT NULL,
        success BIT NOT NULL,
        CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank)
    );
END
"@
Invoke-SqlCmdQuery $initFlywayTable $Database | Out-Null

# Obter versões já aplicadas
$rawVersions = Invoke-SqlCmdQuery "SET NOCOUNT ON; SELECT version FROM dbo.flyway_schema_history WHERE success = 1;" $Database
$appliedVersions = @($rawVersions | Where-Object { $_ -match '^\d+' } | ForEach-Object { $_.Trim() })

# Listar arquivos de migração
$migrationPath = Resolve-Path $MigrationDir
$files = Get-ChildItem -Path $migrationPath -Filter "V*__*.sql" | Sort-Object {
    if ($_.BaseName -match '^V(\d+)') { [int]$Matches[1] } else { 9999 }
}

$rankQuery = Invoke-SqlCmdQuery "SET NOCOUNT ON; SELECT ISNULL(MAX(installed_rank), 0) FROM dbo.flyway_schema_history;" $Database
$currentRank = [int]($rankQuery | Where-Object { $_ -match '^\d+' } | Select-Object -First 1)

$appliedCount = 0
foreach ($file in $files) {
    if ($file.BaseName -match '^V(\d+)__(.*)') {
        $version = $Matches[1]
        $desc = $Matches[2].Replace('_', ' ')

        if ($appliedVersions -contains $version) {
            Write-Host "  [-] V$version ($desc) já aplicada." -ForegroundColor DarkGray
            continue
        }

        Write-Host "  [+] Aplicando V$version ($desc)..." -ForegroundColor Yellow
        $currentRank++
        $stopwatch = [System.Diagnostics.Stopwatch]::StartNew()
        try {
            Invoke-SqlCmdFile -FilePath $file.FullName -Db $Database | Out-Null
            $stopwatch.Stop()
            $elapsedMs = [int]$stopwatch.ElapsedMilliseconds

            $recordQuery = @"
INSERT INTO dbo.flyway_schema_history
(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
($currentRank, '$version', '$desc', 'SQL', '$($file.Name)', 0, SUSER_SNAME(), GETDATE(), $elapsedMs, 1);
"@
            Invoke-SqlCmdQuery $recordQuery $Database | Out-Null
            Write-Host "      Sucesso ($($elapsedMs)ms)" -ForegroundColor Green
            $appliedCount++
        } catch {
            $stopwatch.Stop()
            $elapsedMs = [int]$stopwatch.ElapsedMilliseconds
            $recordQuery = @"
INSERT INTO dbo.flyway_schema_history
(installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success)
VALUES
($currentRank, '$version', '$desc', 'SQL', '$($file.Name)', 0, SUSER_SNAME(), GETDATE(), $elapsedMs, 0);
"@
            try { Invoke-SqlCmdQuery $recordQuery $Database | Out-Null } catch {}
            Write-Error "Falha ao aplicar migração $($file.Name): $_"
            exit 1
        }
    }
}

if ($appliedCount -eq 0) {
    Write-Host "==> Nenhuma migração pendente. Banco de dados já está atualizado!" -ForegroundColor Green
} else {
    Write-Host "==> $appliedCount migração(ões) aplicada(s) com sucesso!" -ForegroundColor Green
}
