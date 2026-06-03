@echo off
REM Double-click from Explorer to start MindVideo dev (Docker + backend + frontend).
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1"
if errorlevel 1 pause
