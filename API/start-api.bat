@echo off
setlocal

cd /d "%~dp0.."

if not exist "node_modules\.bin\tsx.cmd" (
  echo [ERROR] node_modules not found.
  echo Please run "corepack pnpm install" first.
  pause
  exit /b 1
)

if not "%~1"=="" set "SP_API_PORT=%~1"
if "%SP_API_PORT%"=="" set "SP_API_PORT=1145"
if "%SP_API_HOST%"=="" set "SP_API_HOST=0.0.0.0"

netstat -ano | findstr /R /C:":%SP_API_PORT% .*LISTENING" >nul
if %errorlevel%==0 (
  echo [ERROR] Port %SP_API_PORT% is already in use.
  echo Close the existing service or run:
  echo   API\start-api.bat 32584
  pause
  exit /b 1
)

echo Starting SPlayer standalone API...
echo Host: %SP_API_HOST%
echo Port: %SP_API_PORT%
echo.
echo Android API base URL example:
echo   http://YOUR_PC_IP:%SP_API_PORT%/api/netease
echo.
echo Local IPv4 addresses:
for /f "tokens=14 delims= " %%i in ('ipconfig ^| findstr /R /C:"IPv4.*:"') do echo   %%i
echo.

call "node_modules\.bin\tsx.cmd" "API\server.ts"

endlocal
