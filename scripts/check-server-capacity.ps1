param(
    [int]$MinCores = 6,
    [int]$MinHostRamGB = 24,
    [int]$MinDockerRamGB = 24,
    [int]$MinDockerCores = 6
)

$ErrorActionPreference = 'Stop'

function Get-HostCpuCores {
    $cpu = Get-CimInstance Win32_ComputerSystem
    return [int]$cpu.NumberOfLogicalProcessors
}

function Get-HostRamGB {
    $os = Get-CimInstance Win32_OperatingSystem
    $bytes = [double]$os.TotalVisibleMemorySize * 1KB
    return [math]::Round($bytes / 1GB, 2)
}

function Get-DockerInfo {
    docker info --format '{{.NCPU}}|{{.MemTotal}}' 2>$null
}

$hostCores = Get-HostCpuCores
$hostRamGB = Get-HostRamGB

$dockerRaw = Get-DockerInfo
if (-not $dockerRaw) {
    Write-Host 'Docker: indisponible (daemon non démarré ?)' -ForegroundColor Yellow
    Write-Host "Host   : cores=$hostCores ramGB=$hostRamGB"
    exit 2
}

$parts = $dockerRaw -split '\|'
$dockerCores = [int]$parts[0]
$dockerRamBytes = [double]$parts[1]
$dockerRamGB = [math]::Round($dockerRamBytes / 1GB, 2)

$hostOk = ($hostCores -ge $MinCores) -and ($hostRamGB -ge $MinHostRamGB)
$dockerOk = ($dockerCores -ge $MinDockerCores) -and ($dockerRamGB -ge $MinDockerRamGB)

Write-Host "Host   : cores=$hostCores ramGB=$hostRamGB (target >= $MinCores cores / $MinHostRamGB GB)"
Write-Host "Docker : cores=$dockerCores ramGB=$dockerRamGB (target >= $MinDockerCores cores / $MinDockerRamGB GB)"

if ($hostOk -and $dockerOk) {
    Write-Host 'Result : READY ✅' -ForegroundColor Green
    exit 0
}

Write-Host 'Result : NOT READY ❌' -ForegroundColor Red
if (-not $hostOk) {
    Write-Host '- Host machine below target.'
}
if (-not $dockerOk) {
    Write-Host '- Docker Desktop allocation below target (increase CPUs/RAM in Docker settings).'
}

exit 1