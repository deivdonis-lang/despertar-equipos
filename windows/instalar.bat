@echo off
cd /d "%~dp0"
net session >nul 2>&1 || (echo Ejecuta este archivo como administrador: clic derecho ^> Ejecutar como administrador & pause & exit /b 1)
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0instalar.ps1"
pause
