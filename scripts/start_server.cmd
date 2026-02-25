@echo off
setlocal
cd /d %~dp0\..\server

dotnet restore
if errorlevel 1 exit /b 1

dotnet run
