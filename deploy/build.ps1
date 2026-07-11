<#
.SYNOPSIS
  CuteGoals 2.0 — 构建与部署管理脚本（PowerShell 版）
.DESCRIPTION
  支持子命令：help | build-server | build-web | build-docker | up | down | logs | backup | doctor | env-validate
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

# ── 支持平台 ────────────────────────────────────────────────────────────────
$SUPPORTED_PLATFORMS = @("linux/amd64", "linux/arm64")

# ── 路径 ────────────────────────────────────────────────────────────────────
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir\.."
$EnvFile = "$ProjectRoot\.env"
$ComposeFile = "$ScriptDir\docker-compose.yml"

# ── 辅助函数 ────────────────────────────────────────────────────────────────

function Write-Info {
    Write-Host "[信息] $args" -ForegroundColor Green
}

function Write-Warn {
    Write-Host "[警告] $args" -ForegroundColor Yellow
}

function Write-ErrorExit {
    Write-Host "[错误] $args" -ForegroundColor Red
    exit 1
}

function Check-Dependency {
    param([string]$Cmd, [string]$Hint)
    if (-not (Get-Command $Cmd -ErrorAction SilentlyContinue)) {
        Write-ErrorExit "缺少必需命令：$Cmd$($Hint ? " （$Hint）" : '')"
    }
}

function Load-Env {
    if (-not (Test-Path $EnvFile)) {
        Write-Warn ".env 文件不存在（$EnvFile）"
        Write-Warn "请从模板复制：Copy-Item deploy\.env.template .env"
        return $false
    }
    Get-Content $EnvFile | ForEach-Object {
        if ($_ -match '^\s*([^#=]+)\s*=\s*(.*)\s*$') {
            $key = $matches[1].Trim()
            $val = $matches[2].Trim()
            Set-Item -Path "env:$key" -Value $val -ErrorAction SilentlyContinue
        }
    }
    return $true
}

function Validate-Platform {
    param([string]$Platform)
    if ($SUPPORTED_PLATFORMS -contains $Platform) {
        return $true
    }
    Write-Host "UNSUPPORTED_PLATFORM" -ForegroundColor Red
    Write-Host "不受支持的平台：$Platform"
    Write-Host "支持的平台：$($SUPPORTED_PLATFORMS -join ', ')"
    exit 1
}

function Get-Platform {
    param([string]$Specified)
    if ($Specified) {
        Validate-Platform $Specified
        return $Specified
    }
    # 自动检测
    $arch = (uname -m) 2>$null
    if (-not $arch) {
        # Windows
        $env_arch = $env:PROCESSOR_ARCHITECTURE
        if ($env_arch -eq "AMD64") { return "linux/amd64" }
        if ($env_arch -eq "ARM64") { return "linux/arm64" }
        Write-ErrorExit "无法自动检测架构，请使用 --platform 指定"
    }
    switch -wildcard ($arch) {
        "x86_64*" { return "linux/amd64" }
        "aarch64*" { return "linux/arm64" }
        "arm64*" { return "linux/arm64" }
        default { Write-ErrorExit "不受支持的本地架构：$arch" }
    }
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
  build-docker [--platform linux/amd64|linux/arm64] [--no-cache]
                             构建 Docker 镜像（支持多架构）
  up [--detach]              启动所有 Docker Compose 服务
  down                       停止并移除所有 Docker Compose 服务
  logs [服务名]              查看服务日志（默认查看所有）
  backup                     手动触发数据库备份
  doctor                     运行系统诊断
  env-validate               校验 .env 配置是否有效

常用示例：
  .\deploy\build.ps1 build-server            # 构建后端
  .\deploy\build.ps1 build-web               # 构建前端
  .\deploy\build.ps1 build-docker            # 构建 Docker 镜像（当前架构）
  .\deploy\build.ps1 build-docker --platform linux/arm64  # 构建 ARM64 镜像
  .\deploy\build.ps1 up --detach             # 后台启动所有服务
  .\deploy\build.ps1 logs server             # 查看后端日志
  .\deploy\build.ps1 backup                  # 手动备份
  .\deploy\build.ps1 doctor                  # 运行系统诊断
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
    if (-not $?) { Write-ErrorExit "后端构建失败" }
    Pop-Location
    Write-Info "后端构建完成！JAR 包位于 server\web\target\"
}

function Invoke-BuildWeb {
    Check-Dependency "node" "请安装 Node.js 20+"
    Check-Dependency "npm" "请安装 npm 10+"

    Write-Info "开始安装前端依赖..."
    Push-Location "$ProjectRoot\web"
    & npm ci
    if (-not $?) { Write-ErrorExit "npm ci 失败" }

    Write-Info "开始构建前端产物..."
    & npm run build
    if (-not $?) { Write-ErrorExit "前端构建失败" }
    Pop-Location
    Write-Info "前端构建完成！产物位于 web\dist\"
}

function Invoke-BuildDocker {
    Check-Dependency "docker" "请安装 Docker 24+"

    $platform = ""
    $dockerArgs = @()
    $noCache = $false

    # 解析参数
    $remaining = @()
    $i = 0
    $args_list = $args
    while ($i -lt $args_list.Count) {
        switch ($args_list[$i]) {
            "--platform" {
                $i++
                if ($i -ge $args_list.Count) { Write-ErrorExit "--platform 需要参数" }
                $platform = $args_list[$i]
                Validate-Platform $platform
            }
            "--no-cache" {
                $noCache = $true
                Write-Info "禁用 Docker 构建缓存（--no-cache）"
            }
            default {
                $remaining += $args_list[$i]
            }
        }
        $i++
    }

    if (-not $platform) {
        $platform = Get-Platform
        Write-Info "自动检测平台：${platform}"
    }

    Load-Env | Out-Null
    $version = $env:APP_VERSION
    if (-not $version) { $version = "latest" }

    $buildArgs = @()
    if ($noCache) { $buildArgs += "--no-cache" }
    $buildArgs += "--platform"; $buildArgs += $platform

    Write-Info "构建后端 Docker 镜像（${platform}）：mit-modelide-core-server:${version} ..."
    & docker build `
        -f "$ProjectRoot\server\Dockerfile" `
        -t "mit-modelide-core-server:${version}" `
        @buildArgs `
        "$ProjectRoot"
    if (-not $?) { Write-ErrorExit "后端 Docker 镜像构建失败" }

    Write-Info "构建前端 Web Docker 镜像（${platform}）：mit-modelide-core-web:${version} ..."
    & docker build `
        -f "$ProjectRoot\web\Dockerfile" `
        -t "mit-modelide-core-web:${version}" `
        @buildArgs `
        "$ProjectRoot"
    if (-not $?) { Write-ErrorExit "前端 Docker 镜像构建失败" }

    Write-Info "构建 Nginx Docker 镜像（${platform}）：mit-modelide-core-nginx:${version} ..."
    & docker build `
        -f "$ScriptDir\nginx\Dockerfile" `
        -t "mit-modelide-core-nginx:${version}" `
        @buildArgs `
        "$ProjectRoot"
    if (-not $?) { Write-ErrorExit "Nginx Docker 镜像构建失败" }

    Write-Info "Docker 镜像构建完成！"
    Write-Info "  - mit-modelide-core-server:${version} (${platform})"
    Write-Info "  - mit-modelide-core-web:${version} (${platform})"
    Write-Info "  - mit-modelide-core-nginx:${version} (${platform})"
}

function Invoke-Up {
    param([string]$Opt)
    Check-Dependency "docker" "请安装 Docker 24+"
    if (-not (Load-Env)) { Write-ErrorExit ".env 文件缺失，请先创建并配置" }

    $upArgs = @()
    if ($Opt -eq "--detach") {
        $upArgs += "-d"
    }

    Write-Info "启动 Docker Compose 服务..."
    & docker compose --env-file "$EnvFile" -f "$ComposeFile" up @upArgs
    if (-not $?) { Write-ErrorExit "服务启动失败" }

    Write-Info "服务已启动！"
    Write-Info "  前端：http://localhost:80"
    Write-Info "  健康检查：http://localhost:80/api/health"
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
    if (-not (Load-Env)) { Write-ErrorExit ".env 文件缺失，请先创建并配置" }

    Write-Info "手动触发数据库备份..."
    & docker compose --env-file "$EnvFile" -f "$ComposeFile" `
        exec mit-modelide-core-backup `
        /usr/local/bin/backup.sh
}

function Invoke-Doctor {
    Check-Dependency "docker" "请安装 Docker 24+"
    Load-Env | Out-Null

    Write-Host "========================================="
    Write-Host "  CuteGoals 系统诊断 (doctor)"
    Write-Host "========================================="
    Write-Host ""

    $allOk = $true

    # Docker
    Write-Host "▶ 检查 Docker..."
    try {
        & docker info 2>&1 | Out-Null
        Write-Host "  ✓ Docker 运行中"
    } catch {
        Write-Host "  ✗ Docker 未运行或无法访问"
        $allOk = $false
    }

    # 服务状态
    Write-Host ""
    Write-Host "▶ 检查服务状态..."
    $services = @(
        @{Name="mit-modelide-core-postgres"; Label="PostgreSQL"},
        @{Name="mit-modelide-core-redis"; Label="Redis"},
        @{Name="mit-modelide-core-server"; Label="Server"},
        @{Name="mit-modelide-core-nginx"; Label="Nginx"}
    )

    foreach ($svc in $services) {
        $containers = & docker ps --format '{{.Names}}' 2>$null
        if ($containers -contains $svc.Name) {
            $status = & docker inspect --format='{{.State.Health.Status}}' $svc.Name 2>$null
            if (-not $status) { $status = "unknown" }
            Write-Host "  $($svc.Label) ($($svc.Name)): $status"
            if ($status -ne "healthy") { $allOk = $false }
        } else {
            Write-Host "  $($svc.Label) ($($svc.Name)): NOT_RUNNING"
            $allOk = $false
        }
    }

    # 磁盘
    Write-Host ""
    Write-Host "▶ 磁盘空间检查..."
    $drive = (Get-PSDrive C).Free
    $total = (Get-PSDrive C).Used + $drive
    $pct = [math]::Round((1 - $drive / $total) * 100)
    Write-Host "  可用空间: $([math]::Round($drive/1GB, 1)) GB"
    if ($pct -gt 90) {
        Write-Host "  ⚠ 磁盘使用率超过 90% (${pct}%)"
        $allOk = $false
    } else {
        Write-Host "  ✓ 磁盘使用率正常 (${pct}%)"
    }

    Write-Host ""
    if ($allOk) {
        Write-Host "✓ 所有检查通过" -ForegroundColor Green
    } else {
        Write-Host "⚠ 部分检查未通过，请查看上方警告" -ForegroundColor Yellow
    }
}

function Invoke-EnvValidate {
    if (-not (Load-Env)) { Write-ErrorExit ".env 文件缺失" }

    Write-Host "校验 .env 配置..."
    $errors = 0

    $requiredVars = @(
        @{Var="PG_PASSWORD"; Label="PostgreSQL 密码"},
        @{Var="REDIS_PASSWORD"; Label="Redis 密码"},
        @{Var="JWT_SECRET"; Label="JWT 签名密钥"}
    )

    foreach ($entry in $requiredVars) {
        $val = [Environment]::GetEnvironmentVariable($entry.Var)
        if (-not $val) {
            Write-Host "  ✗ $($entry.Var): $($entry.Label) 未设置"
            $errors++
        } elseif ($val -like "changeit*") {
            Write-Host "  ✗ $($entry.Var): $($entry.Label) 为示例值"
            $errors++
        } elseif ($entry.Var -eq "JWT_SECRET" -and $val.Length -lt 32) {
            Write-Host "  ✗ $($entry.Var): $($entry.Label) 长度不足 32 字符"
            $errors++
        } else {
            Write-Host "  ✓ $($entry.Var): 已配置"
        }
    }

    if ($errors -gt 0) {
        Write-Host ""
        Write-ErrorExit "CONFIG_INVALID: $errors 个配置项校验失败，请修正后重试"
    } else {
        Write-Host ""
        Write-Host "✓ 配置校验通过" -ForegroundColor Green
    }
}

# ── 主入口 ──────────────────────────────────────────────────────────────────

if ($Help) {
    Show-Help
    exit 0
}

if (-not $Command) {
    Write-Warn "缺少子命令！请使用以下子命令："
    Show-Help
    exit 1
}

switch ($Command.ToLower()) {
    "help" { Show-Help }
    "build-server" { Invoke-BuildServer $Argument }
    "build-web" { Invoke-BuildWeb }
    "build-docker" {
        # 从剩余参数中解析
        $remainingArgs = @()
        $skipNext = $false
        for ($i = 0; $i -lt $args.Count; $i++) {
            if ($skipNext) { $skipNext = $false; continue }
            if ($args[$i] -eq "--platform" -or $args[$i] -eq "--no-cache") {
                $remainingArgs += $args[$i]
                if ($args[$i] -eq "--platform" -and ($i + 1) -lt $args.Count) {
                    $remainingArgs += $args[$i + 1]
                    $skipNext = $true
                }
            }
        }
        Invoke-BuildDocker $remainingArgs
    }
    "up" { Invoke-Up $Argument }
    "down" { Invoke-Down }
    "logs" { Invoke-Logs $Argument }
    "backup" { Invoke-Backup }
    "doctor" { Invoke-Doctor }
    "env-validate" { Invoke-EnvValidate }
    default {
        Write-ErrorExit "未知子命令：$Command`n使用 .\deploy\build.ps1 help 查看可用子命令"
    }
}
