$ports = @(9528, 8088, 8250, 8340)
foreach ($port in $ports) {
    $connections = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue |
        Where-Object State -eq "Listen"
    foreach ($connection in $connections) {
        Write-Host "Stopping process $($connection.OwningProcess) on port $port..."
        Stop-Process -Id $connection.OwningProcess -Force -ErrorAction SilentlyContinue
    }
}
Write-Host "Local WeCross services stopped." -ForegroundColor Green