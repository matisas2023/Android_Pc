param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$venvDir = Join-Path $root ".venv"
$pythonPath = Join-Path $venvDir "Scripts\\python.exe"

if (-not (Test-Path $pythonPath)) {
    & $PythonExe -m venv $venvDir
}

& $pythonPath -m pip install --upgrade pip
& $pythonPath -m pip install -r (Join-Path $root "requirements.txt")

if (-not $env:PC_REMOTE_API_TOKEN) {
    $env:PC_REMOTE_API_TOKEN = "change-me"
}

& $pythonPath -m uvicorn main:app --host 0.0.0.0 --port 8000
