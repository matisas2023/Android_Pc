@echo off
setlocal
set SERVER_DIR=%~dp0
pushd %SERVER_DIR%

if "%PC_REMOTE_API_TOKEN%"=="" (
  echo Using default token: change-me
)

dotnet run --project "%SERVER_DIR%PCRemoteServer.csproj"

popd
endlocal
