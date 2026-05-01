@echo off
setlocal

set "ROOT=%~dp0.."
set "APP_DIR=%ROOT%\dash-apps\web-ambient"
set "PORT=8081"

if not exist "%APP_DIR%\index.html" (
  echo Error: ambient app not found at "%APP_DIR%"
  exit /b 1
)

echo.
echo  Ambient dev URLs:
echo    http://127.0.0.1:%PORT%/dev.html
echo    http://127.0.0.1:%PORT%/index.html
echo.

start http://127.0.0.1:%PORT%/dev.html

pushd "%APP_DIR%"
npx --yes live-server --port=%PORT% --no-browser
popd
