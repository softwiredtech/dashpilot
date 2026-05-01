@echo off
setlocal

set "ROOT=%~dp0.."
set "SIM_DIR=%ROOT%\simulator"
set "ROUTE=routes\route_minimal_ambient_30s.jsonl"

if not exist "%SIM_DIR%\server.py" (
  echo Error: simulator not found at "%SIM_DIR%"
  exit /b 1
)

pushd "%SIM_DIR%"

where py >nul 2>nul
if %ERRORLEVEL%==0 (
  py -3 server.py --route "%ROUTE%" %*
) else (
  python server.py --route "%ROUTE%" %*
)

popd
