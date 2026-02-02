param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvDir = Join-Path $root ".venv"
$pythonPath = Join-Path $venvDir "Scripts\\python.exe"
$requirementsPath = Join-Path $root "requirements.txt"
$requirementsHashPath = Join-Path $venvDir ".requirements.sha256"

if (-not (Test-Path $pythonPath)) {
    & $PythonExe -m venv $venvDir
}

$pythonExec = $pythonPath
if (-not (Test-Path $pythonExec)) {
    $pythonExec = $PythonExe
}

$requirementsHash = (Get-FileHash -Path $requirementsPath -Algorithm SHA256).Hash
$needsInstall = $true
if (Test-Path $requirementsHashPath) {
    $storedHash = Get-Content -Path $requirementsHashPath -ErrorAction SilentlyContinue
    if ($storedHash -eq $requirementsHash) {
        $needsInstall = $false
    }
}

if ($needsInstall) {
    Push-Location $root
    try {
        & $pythonExec -m pip install --disable-pip-version-check --no-input -r $requirementsPath
        $requirementsHash | Set-Content -Path $requirementsHashPath
    } finally {
        Pop-Location
    }
}

if (-not $env:PC_REMOTE_API_TOKEN) {
    $env:PC_REMOTE_API_TOKEN = "change-me"
}

Push-Location $root
try {
    & $pythonExec -m uvicorn main:app --app-dir $root --host 0.0.0.0 --port 8000
} finally {
    Pop-Location
}
