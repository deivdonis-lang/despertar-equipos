@echo off
net session >nul 2>&1 || (echo Ejecuta este archivo como administrador & pause & exit /b 1)
schtasks /End /TN AgenteWOL >nul 2>&1
schtasks /Delete /TN AgenteWOL /F
rmdir /s /q "%ProgramData%\AgenteWOL"
echo Agente WOL desinstalado.
pause
