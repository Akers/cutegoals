# CuteGoals 2.0 — 后端开发启动脚本（Windows PowerShell）
# 功能：交互式输入中间件地址、设置环境变量、启动 Spring Boot 并实时输出日志
# 用法：.\scripts\start-dev.ps1 [-Logs] [-UseEnv]
#   -Logs       实时查看开发日志
#   -UseEnv     跳过交互，直接使用 .env.dev 配置启动

param(
    [switch]$Logs,
    [switch]$UseEnv
)

$ErrorActionPreference = "Stop"

$REPO_ROOT = Split-Path -Parent $PSScriptRoot
$SERVER_DIR = Join-Path $REPO_ROOT "server"
$ENV_FILE = Join-Path $REPO_ROOT ".env.dev"

function Write-Info { param([string]$msg) Write-Host "[INFO] $msg" -ForegroundColor Cyan }
function Write-Ok { param([string]$msg) Write-Host "[OK] $msg" -ForegroundColor Green }
function Write-Warn { param([string]$msg) Write-Host "[WARN] $msg" -ForegroundColor Yellow }
function Write-Err { param([string]$msg) Write-Host "[ERR] $msg" -ForegroundColor Red }

function Print-Header {
    Write-Host ""
    Write-Host "╔══════════════════════════════════════════════════════╗"
    Write-Host "║      CuteGoals 2.0 — 后端开发启动脚本（Windows）      ║"
    Write-Host "╚══════════════════════════════════════════════════════╝"
    Write-Host ""
}

function Test-Command {
    param([string]$cmd, [string]$minVersion)
    if (-not (Get-Command $cmd -ErrorAction SilentlyContinue)) {
        Write-Err "$cmd 未安装，请先安装 $cmd $minVersion+"
        exit 1
    }
    Write-Ok "$cmd 已安装"
}

function Test-JavaVersion {
    $versionLine = (java -version 2>&1) | Select-String -Pattern 'version "?(\d+)'
    if (-not $versionLine) {
        Write-Err "无法识别 JDK 版本"
        exit 1
    }
    $version = [int]$versionLine.Matches.Groups[1].Value
    if ($version -lt 21) {
        Write-Err "JDK 版本过低：$version，需要 JDK 21+"
        exit 1
    }
    Write-Ok "JDK 版本：$version"
}

function Test-MavenVersion {
    $versionLine = (mvn -version 2>&1) | Select-String -Pattern 'Apache Maven ([\d\.]+)'
    if (-not $versionLine) {
        Write-Err "无法识别 Maven 版本"
        exit 1
    }
    $version = $versionLine.Matches.Groups[1].Value
    Write-Ok "Maven 版本：$version"
}

function Test-Postgres {
    param([string]$Host = "localhost", [string]$Port = "35432")
    $pgIsReady = Get-Command pg_isready -ErrorAction SilentlyContinue
    if ($pgIsReady) {
        $process = Start-Process -FilePath "pg_isready" -ArgumentList "-h", $Host, "-p", $Port -NoNewWindow -PassThru -Wait
        if ($process.ExitCode -ne 0) {
            Write-Warn "PostgreSQL ${Host}:${Port} 未响应，请先启动数据库"
            Write-Info "手动初始化：psql -U postgres -h ${Host} -p ${Port} -f deploy/postgres-init.sql"
            Write-Info "Docker 启动：docker compose -f deploy/docker-compose.yml up -d mit-modelide-core-postgres mit-modelide-core-redis"
        } else {
            Write-Ok "PostgreSQL ${Host}:${Port} 可连接"
        }
    }
}

function Load-ExistingConfig {
    if (Test-Path $ENV_FILE) {
        Write-Info "检测到历史开发配置 $ENV_FILE，将用作默认值"
        Get-Content $ENV_FILE | ForEach-Object {
            if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
                [System.Environment]::SetEnvironmentVariable($matches[1], $matches[2], "Process")
            }
        }
    }
}

function Prompt-Input {
    param(
        [string]$Message,
        [string]$DefaultValue = ""
    )
    if ($DefaultValue) {
        Write-Info "$Message [默认: $DefaultValue]:"
    } else {
        Write-Info "$Message:"
    }
    $input = Read-Host
    if ([string]::IsNullOrWhiteSpace($input) -and -not [string]::IsNullOrWhiteSpace($DefaultValue)) {
        return $DefaultValue
    }
    return $input
}

function Generate-JwtSecret {
    $bytes = [System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
    return [Convert]::ToBase64String($bytes)
}

function Save-Config {
    param(
        [string]$PG_HOST,
        [string]$PG_PORT,
        [string]$PG_DATABASE,
        [string]$PG_USER,
        [string]$PG_PASSWORD,
        [string]$PG_SCHEMA,
        [string]$REDIS_HOST,
        [string]$REDIS_PORT,
        [string]$REDIS_PASSWORD,
        [string]$CUTEGOALS_JWT_SECRET,
        [string]$PORT
    )
    $content = @"
# CuteGoals 2.0 — 开发环境配置（由 start-dev.ps1 生成）
# 此文件仅用于本地开发，请勿提交到 Git

PG_HOST=$PG_HOST
PG_PORT=$PG_PORT
PG_DATABASE=$PG_DATABASE
PG_USER=$PG_USER
PG_PASSWORD=$PG_PASSWORD
PG_SCHEMA=$PG_SCHEMA

REDIS_HOST=$REDIS_HOST
REDIS_PORT=$REDIS_PORT
REDIS_PASSWORD=$REDIS_PASSWORD

CUTEGOALS_JWT_SECRET=$CUTEGOALS_JWT_SECRET
APP_PRODUCTION=false
PORT=$PORT
"@
    $content | Out-File -FilePath $ENV_FILE -Encoding UTF8
    Write-Ok "配置已保存到 $ENV_FILE"
}

function Start-Backend {
    param([switch]$UseEnv)

    Print-Header

    if ($UseEnv) {
        if (-not (Test-Path $ENV_FILE)) {
            Write-Err "未找到 $ENV_FILE，无法使用 -UseEnv 模式启动"
            Write-Info "请先正常运行一次 .\scripts\start-dev.ps1 以生成 .env.dev 配置"
            exit 1
        }
        Write-Warn "已启用 -UseEnv 模式，跳过交互，直接使用 $ENV_FILE 配置"
    }

    Test-Command "java" "21"
    Test-JavaVersion
    Test-Command "mvn" "3.9"
    Test-MavenVersion

    Load-ExistingConfig

    if ($UseEnv) {
        # 直接读取已加载的环境变量，跳过交互
        $PG_HOST = [System.Environment]::GetEnvironmentVariable("PG_HOST", "Process")
        $PG_PORT = [System.Environment]::GetEnvironmentVariable("PG_PORT", "Process")
        $PG_DATABASE = [System.Environment]::GetEnvironmentVariable("PG_DATABASE", "Process")
        $PG_USER = [System.Environment]::GetEnvironmentVariable("PG_USER", "Process")
        $PG_PASSWORD = [System.Environment]::GetEnvironmentVariable("PG_PASSWORD", "Process")
        $PG_SCHEMA = [System.Environment]::GetEnvironmentVariable("PG_SCHEMA", "Process")
        $REDIS_HOST = [System.Environment]::GetEnvironmentVariable("REDIS_HOST", "Process")
        $REDIS_PORT = [System.Environment]::GetEnvironmentVariable("REDIS_PORT", "Process")
        $REDIS_PASSWORD = [System.Environment]::GetEnvironmentVariable("REDIS_PASSWORD", "Process")
        $CUTEGOALS_JWT_SECRET = [System.Environment]::GetEnvironmentVariable("CUTEGOALS_JWT_SECRET", "Process")
        $PORT = [System.Environment]::GetEnvironmentVariable("PORT", "Process")

        Write-Host ""
        Write-Info "已加载配置："
        Write-Info "  PostgreSQL: ${PG_HOST}:${PG_PORT}/${PG_DATABASE} (schema=${PG_SCHEMA})"
        Write-Info "  Redis: ${REDIS_HOST}:${REDIS_PORT}"
        Write-Info "  后端端口: ${PORT}"
    } else {
        Write-Host ""
        Write-Info "请输入 PostgreSQL 连接信息（回车使用默认值）"
        $PG_HOST = Prompt-Input -Message "PostgreSQL 主机" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_HOST", "Process") -or "localhost")
        $PG_PORT = Prompt-Input -Message "PostgreSQL 端口" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_PORT", "Process") -or "35432")
        $PG_DATABASE = Prompt-Input -Message "PostgreSQL 数据库名" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_DATABASE", "Process") -or "cutegoals")
        $PG_USER = Prompt-Input -Message "PostgreSQL 用户名" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_USER", "Process") -or "cutegoals")
        $PG_PASSWORD = Prompt-Input -Message "PostgreSQL 密码" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_PASSWORD", "Process") -or "cutegoals")
        $PG_SCHEMA = Prompt-Input -Message "PostgreSQL Schema" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PG_SCHEMA", "Process") -or "cutegoals")

        Write-Host ""
        Write-Info "请输入 Redis 连接信息（回车使用默认值）"
        $REDIS_HOST = Prompt-Input -Message "Redis 主机" -DefaultValue ([System.Environment]::GetEnvironmentVariable("REDIS_HOST", "Process") -or "localhost")
        $REDIS_PORT = Prompt-Input -Message "Redis 端口" -DefaultValue ([System.Environment]::GetEnvironmentVariable("REDIS_PORT", "Process") -or "6379")
        $REDIS_PASSWORD = Prompt-Input -Message "Redis 密码" -DefaultValue ([System.Environment]::GetEnvironmentVariable("REDIS_PASSWORD", "Process") -or "")

        Write-Host ""
        Write-Info "请输入应用配置"
        $existingJwt = [System.Environment]::GetEnvironmentVariable("CUTEGOALS_JWT_SECRET", "Process")
        if ([string]::IsNullOrWhiteSpace($existingJwt)) {
            $existingJwt = Generate-JwtSecret
            Write-Warn "已自动生成 JWT_SECRET，如要固定可手动修改 .env.dev"
        }
        $CUTEGOALS_JWT_SECRET = Prompt-Input -Message "JWT Secret" -DefaultValue $existingJwt
        $PORT = Prompt-Input -Message "后端服务端口" -DefaultValue ([System.Environment]::GetEnvironmentVariable("PORT", "Process") -or "8080")

        Write-Host ""
        $saveChoice = Read-Host "是否将本次配置保存到 .env.dev 供下次使用？ [Y/n]"
        if ([string]::IsNullOrWhiteSpace($saveChoice) -or $saveChoice -match '^[Yy]$') {
            Save-Config -PG_HOST $PG_HOST -PG_PORT $PG_PORT -PG_DATABASE $PG_DATABASE -PG_USER $PG_USER -PG_PASSWORD $PG_PASSWORD -PG_SCHEMA $PG_SCHEMA -REDIS_HOST $REDIS_HOST -REDIS_PORT $REDIS_PORT -REDIS_PASSWORD $REDIS_PASSWORD -CUTEGOALS_JWT_SECRET $CUTEGOALS_JWT_SECRET -PORT $PORT
        }
    }

    Test-Postgres -Host $PG_HOST -Port $PG_PORT

    Write-Host ""
    Write-Info "正在启动 CuteGoals 2.0 后端服务..."
    Write-Info "服务启动后，下方将实时输出日志。按 Ctrl+C 停止服务。"
    Write-Host ""

    [System.Environment]::SetEnvironmentVariable("PG_HOST", $PG_HOST, "Process")
    [System.Environment]::SetEnvironmentVariable("PG_PORT", $PG_PORT, "Process")
    [System.Environment]::SetEnvironmentVariable("PG_DATABASE", $PG_DATABASE, "Process")
    [System.Environment]::SetEnvironmentVariable("PG_USER", $PG_USER, "Process")
    [System.Environment]::SetEnvironmentVariable("PG_PASSWORD", $PG_PASSWORD, "Process")
    [System.Environment]::SetEnvironmentVariable("PG_SCHEMA", $PG_SCHEMA, "Process")
    [System.Environment]::SetEnvironmentVariable("REDIS_HOST", $REDIS_HOST, "Process")
    [System.Environment]::SetEnvironmentVariable("REDIS_PORT", $REDIS_PORT, "Process")
    [System.Environment]::SetEnvironmentVariable("REDIS_PASSWORD", $REDIS_PASSWORD, "Process")
    [System.Environment]::SetEnvironmentVariable("CUTEGOALS_JWT_SECRET", $CUTEGOALS_JWT_SECRET, "Process")
    [System.Environment]::SetEnvironmentVariable("APP_PRODUCTION", "false", "Process")
    [System.Environment]::SetEnvironmentVariable("PORT", $PORT, "Process")

    Set-Location $SERVER_DIR
    & mvn clean -pl web -am spring-boot:run -DskipTests "-Dspring-boot.run.jvmArguments=-Dfile.encoding=UTF-8"
}

# 支持 -Logs 参数查看日志
if ($Logs) {
    $logFile = Join-Path $REPO_ROOT "logs\cutegoals-dev.log"
    if (Test-Path $logFile) {
        Get-Content $logFile -Wait -Tail 50
    } else {
        Write-Err "未找到日志文件：$logFile"
        exit 1
    }
} else {
    Start-Backend -UseEnv:$UseEnv
}
