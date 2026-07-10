<#
.SYNOPSIS
  CuteGoals 2.0 — 构建与部署管理脚本（PowerShell 版）
.DESCRIPTION
  支持子命令：help | build-server | build-web | build-docker | up | down | logs | backup
  使用方式：.\deploy\build.ps1 <子命令> [选项]
#>

param(
    [Parameter(Position = 0)]
    [string]$Command = "",

    [Parameter(Position = 1)]
    [string]$Argument = "",

    [Alias("h")]
    [switch]$Help
)

# ── 辅助函数 ────────────────────────────────────────────────────────────────

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\.."
$EnvFile = "$ProjectRoot\.env"
$ComposeFile = "$ScriptDir\docker-compose.yml"

function Write-Info {
    Write-Host "[信息] $args" -ForegroundColor Green
}

function Write-Warn {
    Write-Host "[警告] $args" -ForegroundColor Yellow
}

function Write-Error {
    Write-Host "[错误] $args" -ForegroundColor Red
    exit 1
}

function Check-Dependency {
    param([string]$Cmd, [string]$Hint)
    if (-not (Get-Command $Cmd -ErrorAction SilentlyContinue)) {
        Write-Error "缺少必需命令：$Cmd$($Hint ? " （$Hint）" : '')"
    }
}

function Load-Env {
    if (-not (Test-Path $EnvFile)) {
        Write-Warn ".env 文件不存在（$EnvFile）"
        Write-Warn "请从模板复制：Copy-Item deploy\.env.template .env"
        return $false
    }
    # PowerShell 加载 .env
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)\s*=\s*(.*)\s*$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            Set-Item -Path "env:$key" -Value $val -ErrorAction SilentlyContinue
        }
    }
    return $true
}

# ── 帮助信息 ────────────────────────────────────────────────────────────────

function Show-Help {
    @"
CuteGoals 2.0 — 构建与部署管理脚本（PowerShell 版）

用法：
  .\deploy\build.ps1 <子命令> [选项]

子命令：
  help                       显示此帮助信息
  build-server [--skip-tests] 构建后端 JAR 包
  build-web                  构建前端产物
  build-docker [--no-cache]  构建后端和前端 Docker 镜像
  up [--detach]              启动所有 Docker Compose 服务
  down                       停止并移除所有 Docker Compose 服务
  logs [服务名]              查看服务日志（默认查看所有）
  backup                     手动触发数据库备份

常用示例：
  .\deploy\build.ps1 build-server            # 构建后端
  .\deploy\build.ps1 build-web               # 构建前端
  .\deploy\build.ps1 build-docker            # 构建 Docker 镜像
  .\deploy\build.ps1 up --detach             # 后台启动所有服务
  .\deploy\build.ps1 logs server             # 查看后端日志
  .\deploy\build.ps1 backup                  # 手动备份
"@
}

# ── 子命令实现 ──────────────────────────────────────────────────────────────

function Invoke-BuildServer {
    param([string]$Opt)
    Check-Dependency "java" "请安装 JDK 21+"
    Check-Dependency "mvn" "请安装 Maven 3.9+"

    $mvnArgs = @("package", "-pl", "web", "-am", "-B", "-q")
    if ($Opt -eq "--skip-tests") {
        $mvnArgs += "-DskipTests"
        Write-Info "跳过测试（--skip-tests）"
    }

    Write-Info "开始构建后端 JAR 包..."
    Push-Location "$ProjectRoot\server"
    & mvn $mvnArgs
    if (-not $?) { Write-Error "后端构建失败" }
    Pop-Location
    Write-Info "后端构建完成！JAR 包位于 server\web\target\"
}

function Invoke-BuildWeb {
    Check-Dependency "node" "请安装 Node.js 20+"
    Check-Dependency "npm" "请安装 npm 10+"

    Write-Info "开始安装前端依赖..."
    Push-Location "$ProjectRoot\web"
    & npm ci
    if (-not $?) { Write-Error "npm ci 失败" }

    Write-Info "开始构建前端产物..."
    & npm run build
    if (-not $?) { Write-Error "前端构建失败" }
    Pop-Location
    Write-Info "前端构建完成！产物位于 web\dist\"
}

function Invoke-BuildDocker {
    param([string]$Opt)
    Check-Dependency "docker" "请安装 Docker 24+"

    $dockerArgs = @()
    if ($Opt -eq "--no-cache") {
        $dockerArgs += "--no-cache"
        Write-Info "禁用 Docker 构建缓存（--no-cache）"
    }

    Load-Env | Out-Null
    $version = $env:APP_VERSION
    if (-not $version) { $version = "latest" }

    Write-Info "构建后端 Docker 镜像：mit-modelide-core-server:${version} ..."
    & docker build `
        -f "$ScriptDir\docker\Dockerfile.server" `
        -t "mit-modelide-core-server:${version}" `
        @dockerArgs `
        "$ProjectRoot"
    if (-not $?) { Write-Error "后端 Docker 镜像构建失败" }

    Write-Info "构建前端 Nginx Docker 镜像：mit-modelide-core-nginx:${version} ..."
    & docker build `
        -f "$ScriptDir\docker\Dockerfile.nginx" `
        -t "mit-modelide-core-nginx:${version}" `
        @dockerArgs `
        "$ProjectRoot"
    if (-not $?) { Write-Error "前端 Docker 镜像构建失败" }

    Write-Info "Docker 镜像构建完成！"
    Write-Info "  - mit-modelide-core-server:${version}"
    Write-Info "  - mit-modelide-core-nginx:${version}"
}

function Invoke-Up {
    param([string]$Opt)
    Check-Dependency "docker" "请安装 Docker 24+"

    if (-not (Load-Env)) { Write-Error ".env 文件缺失，请先创建并配置" }

    $upArgs = @()
    if ($Opt -eq "--detach") {
        $upArgs += "-d"
    }

    Write-Info "启动 Docker Compose 服务..."
    & docker compose --env-file "$EnvFile" -f "$ComposeFile" up @upArgs
    if (-not $?) { Write-Error "服务启动失败" }

    Write-Info "服务已启动！"
    Write-Info "  前端：http://localhost:80"
    Write-Info "  后端：http://localhost:8080"
    Write-Info "  健康检查：http://localhost:8080/api/health"
}

function Invoke-Down {
    Check-Dependency "docker" "请安装 Docker 24+"
    Load-Env | Out-Null

    Write-Info "停止并移除 Docker Compose 服务..."
    & docker compose --env-file "$EnvFile" -f "$ComposeFile" down
    Write-Info "服务已停止。"
}

function Invoke-Logs {
    param([string]$Service)
    Check-Dependency "docker" "请安装 Docker 24+"
    Load-Env | Out-Null

    if ($Service) {
        Write-Info "查看服务日志：$Service"
        & docker compose --env-file "$EnvFile" -f "$ComposeFile" logs -f $Service
    } else {
        Write-Info "查看所有服务日志..."
        & docker compose --env-file "$EnvFile" -f "$ComposeFile" logs -f
    }
}

function Invoke-Backup {
    Check-Dependency "docker" "请安装 Docker 24+"
    if (-not (Load-Env)) { Write-Error ".env 文件缺失，请先创建并配置" }

    $timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $backupFile = "$ProjectRoot\backup_$timestamp.sql"

    Write-Info "手动触发数据库备份..."
    & docker compose --env-file "$EnvFile" -f "$ComposeFile" `
        exec mit-modelide-core-mysql `
        mysqldump --single-transaction `
        -u"$($env:MYSQL_USER)" `
        -p"$($env:MYSQL_PASSWORD)" `
        "$($env:MYSQL_DATABASE)" `
        > $backupFile

    Write-Info "备份完成！文件位于：$backupFile"
}

# ── 主入口 ──────────────────────────────────────────────────────────────────

# 处理 -Help 开关
if ($Help) {
    Show-Help
    exit 0
}

# 无参数时显示帮助
if (-not $Command) {
    Write-Warn "缺少子命令！请使用以下子命令："
    Show-Help
    exit 1
}

switch ($Command.ToLower()) {
    "help" { Show-Help }
    "build-server" { Invoke-BuildServer $Argument }
    "build-web" { Invoke-BuildWeb }
    "build-docker" { Invoke-BuildDocker $Argument }
    "up" { Invoke-Up $Argument }
    "down" { Invoke-Down }
    "logs" { Invoke-Logs $Argument }
    "backup" { Invoke-Backup }
    default {
        Write-Error "未知子命令：$Command`n使用 .\deploy\build.ps1 help 查看可用子命令"
    }
}
