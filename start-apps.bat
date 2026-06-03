@echo off
REM Start only backend + frontend (Docker must already be running).
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1" -SkipDocker
if errorlevel 1 pause
