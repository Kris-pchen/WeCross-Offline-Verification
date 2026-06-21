param(
    [ValidateSet("mock", "sdk")]
    [string]$Mode = "mock",

    [ValidateSet("local", "hybrid")]
    [string]$Topology = "local",

    [string]$CloudRouter = "127.0.0.1:8250"
)


$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$accountManagerDir = Join-Path $root "WeCross-Account-Manager\dist"
$routerDir = Join-Path $root "WeCross\dist\routers\127.0.0.1-8250-25500"
$verificationDir = Join-Path $root "WerCross-Offline_Verify"
$verificationConfig = Join-Path $verificationDir "src\main\resources\application.toml"
$webAppDir = Join-Path $root "WeCross-WebApp"

function Get-ListeningProcessId([int]$Port) {
    $connection = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue |
        Where-Object State -eq "Listen" |
        Select-Object -First 1
    if ($connection) { return $connection.OwningProcess }
    return $null
}

function Wait-ForPort([string]$Name, [int]$Port, [int]$TimeoutSeconds = 60) {
    for ($i = 0; $i -lt $TimeoutSeconds; $i++) {
        if (Get-ListeningProcessId $Port) {
            Write-Host "[OK] $Name is listening on $Port" -ForegroundColor Green
            return
        }
        Start-Sleep -Seconds 1
    }
    throw "$Name did not start on port $Port. Check its log file."
}

function Assert-Directory([string]$Path) {
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Directory not found: $Path"
    }
}

if ($Topology -eq "local") {
    Assert-Directory $accountManagerDir
    Assert-Directory $routerDir
}
Assert-Directory $verificationDir
Assert-Directory $webAppDir

if ($Topology -eq "local" -and -not (Get-ListeningProcessId 8340)) {
    Write-Host "Starting WeCross Account Manager..."
    $arguments = @(
        "-Dfile.encoding=UTF-8",
        "-Djava.security.properties=./.wecross.security",
        "-Djdk.sunec.disableNative=false",
        "-Djdk.tls.namedGroups=secp256k1,x25519,secp256r1,secp384r1,secp521r1,x448,ffdhe2048,ffdhe3072,ffdhe4096,ffdhe6144,ffdhe8192",
        "-cp", "apps/*;lib/*;conf;plugin/*",
        "com.webank.wecross.account.service.Application"
    )
    Start-Process -FilePath "java" -ArgumentList $arguments -WorkingDirectory $accountManagerDir `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $accountManagerDir "start.out") `
        -RedirectStandardError (Join-Path $accountManagerDir "start.err") | Out-Null
}
if ($Topology -eq "local") {
    Wait-ForPort "Account Manager" 8340
}

if ($Topology -eq "local" -and -not (Get-ListeningProcessId 8250)) {
    Write-Host "Starting WeCross Router..."
    $arguments = @(
        "-Dfile.encoding=UTF-8",
        "-Djdk.tls.client.protocols=TLSv1.2",
        "-Djava.security.properties=./.wecross.security",
        "-Djdk.sunec.disableNative=false",
        "-Djdk.tls.namedGroups=SM2,secp256k1,x25519,secp256r1,secp384r1,secp521r1,x448,ffdhe2048,ffdhe3072,ffdhe4096,ffdhe6144,ffdhe8192",
        "-cp", "apps/*;lib/*;conf;plugin/*;./",
        "com.webank.wecross.Service"
    )
    Start-Process -FilePath "java" -ArgumentList $arguments -WorkingDirectory $routerDir `
        -WindowStyle Hidden -RedirectStandardOutput (Join-Path $routerDir "start.out") `
        -RedirectStandardError (Join-Path $routerDir "start.err") | Out-Null
}
if ($Topology -eq "local") {
    Wait-ForPort "WeCross Router" 8250
}

$routerServer = if ($Topology -eq "hybrid") { $CloudRouter } else { "127.0.0.1:8250" }
$configText = Get-Content -LiteralPath $verificationConfig -Raw
$configText = $configText -replace "server\s*=\s*'[^']+'", "server = '$routerServer'"
[System.IO.File]::WriteAllText($verificationConfig, $configText, [System.Text.UTF8Encoding]::new($false))

if (-not (Get-ListeningProcessId 8088)) {
    Write-Host "Starting offline verification API in $Mode mode..."
    $command = "set WECROSS_MODE=$Mode&&set WECROSS_ROUTER_URL=http://$routerServer&&mvn spring-boot:run > verification-api.log 2>&1"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $command -WorkingDirectory $verificationDir `
        -WindowStyle Hidden | Out-Null
}
Wait-ForPort "Verification API" 8088 120

if (-not (Get-ListeningProcessId 9528)) {
    Write-Host "Starting WeCross WebApp..."
    $command = "npm.cmd run dev -- --no-open > wecross-webapp-dev.log 2>&1"
    Start-Process -FilePath "cmd.exe" -ArgumentList "/c", $command -WorkingDirectory $webAppDir `
        -WindowStyle Hidden | Out-Null
}
Wait-ForPort "WeCross WebApp" 9528 120

Write-Host ""
Write-Host "All local services are running." -ForegroundColor Green
Write-Host "WebApp:          http://localhost:9528"
Write-Host "Verification API: http://localhost:8088/api/verification/health"
Write-Host "Router:           $routerServer"
Write-Host "Mode:             $Mode"
Write-Host "Topology:         $Topology"
Write-Host "SDK Router:       $routerServer"