param(
    [string]$DotnetExe = "dotnet"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectPath = Join-Path $root "Server.csproj"

if (-not $env:PC_REMOTE_API_TOKEN) {
    $env:PC_REMOTE_API_TOKEN = "change-me"
}

Push-Location $root
try {
    & $DotnetExe run --project $projectPath --urls http://0.0.0.0:8000
} finally {
    Pop-Location
}
