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
    Write-Host 'Docker: indisponible (daemon non demarre ?)' -ForegroundColor Yellow
    Write-Host "Host   : cores=$hostCores ramGB=$hostRamGB"
    exit 2
}

$parts = $dockerRaw -split '\|'
$dockerCores = [int]$parts[0]
$dockerRamBytes = [double]$parts[1]
$dockerRamGB = [math]::Round($dockerRamBytes / 1GB, 2)

$hostBelow = ($hostCores -lt $MinCores) -or ($hostRamGB -lt $MinHostRamGB)
$dockerBelow = ($dockerCores -lt $MinDockerCores) -or ($dockerRamGB -lt $MinDockerRamGB)

Write-Host "Host   : cores=$hostCores ramGB=$hostRamGB (budget max $MinCores cores / $MinHostRamGB GB)"
Write-Host "Docker : cores=$dockerCores ramGB=$dockerRamGB (budget max $MinDockerCores cores / $MinDockerRamGB GB)"

if (-not $hostBelow -and -not $dockerBelow) {
    Write-Host 'Result : OK ?' -ForegroundColor Green
    exit 0
}

Write-Host 'Result : BELOW BUDGET ??' -ForegroundColor Yellow
if ($hostBelow) {
    Write-Host '- Host below budget; performance may be reduced.'
}
if ($dockerBelow) {
    Write-Host '- Docker allocation below budget; performance may be reduced.'
}

exit 0
