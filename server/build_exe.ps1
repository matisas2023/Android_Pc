param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$distDir = Join-Path $root "dist"
$venvDir = Join-Path $root ".venv-build"
$pythonPath = Join-Path $venvDir "Scripts\\python.exe"

if (-not (Test-Path $pythonPath)) {
    & $PythonExe -m venv $venvDir
}

& $pythonPath -m pip install --upgrade pip
& $pythonPath -m pip install -r (Join-Path $root "requirements.txt")
& $pythonPath -m pip install -r (Join-Path $root "requirements-dev.txt")

& $pythonPath -m pyinstaller `
    --clean `
    --onefile `
    --name pc-remote-server `
    --distpath $distDir `
    --collect-all fastapi `
    --collect-all pydantic `
    --collect-all starlette `
    --collect-submodules uvicorn `
    --collect-submodules pyautogui `
    --collect-submodules pymsgbox `
    --collect-submodules pytweening `
    --collect-submodules pyscreeze `
    --collect-submodules mouseinfo `
    --collect-submodules psutil `
    --collect-submodules mss `
    (Join-Path $root "main.py")

Write-Host "Executable created at $distDir\pc-remote-server.exe"
