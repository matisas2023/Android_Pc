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

$requirementsHash = (Get-FileHash -Path $requirementsPath -Algorithm SHA256).Hash
$needsInstall = $true
if (Test-Path $requirementsHashPath) {
    $storedHash = Get-Content -Path $requirementsHashPath -ErrorAction SilentlyContinue
    if ($storedHash -eq $requirementsHash) {
        $needsInstall = $false
    }
}

if ($needsInstall) {
    & $pythonPath -m pip install --disable-pip-version-check --no-input -r $requirementsPath
    $requirementsHash | Set-Content -Path $requirementsHashPath
}

if (-not $env:PC_REMOTE_API_TOKEN) {
    $env:PC_REMOTE_API_TOKEN = "change-me"
}

& $pythonPath -m uvicorn main:app --host 0.0.0.0 --port 8000
