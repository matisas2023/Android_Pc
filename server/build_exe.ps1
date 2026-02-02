param(
    [string]$PythonExe = "python"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$distDir = Join-Path $root "dist"

& $PythonExe -m pyinstaller `
    --clean `
    --onefile `
    --name pc-remote-server `
    --distpath $distDir `
    --hidden-import pyautogui `
    --hidden-import psutil `
    --hidden-import mss `
    --hidden-import mss.tools `
    --hidden-import fastapi `
    --hidden-import uvicorn `
    (Join-Path $root "main.py")

Write-Host "Executable created at $distDir\pc-remote-server.exe"
