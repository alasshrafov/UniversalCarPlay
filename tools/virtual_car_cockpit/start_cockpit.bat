@echo off
title CarTwin - Virtual BYD Car Monitor Cockpit
color 0b
echo ==========================================================
echo    CarTwin Virtual BYD DiLink Monitor & CarPlay Cockpit
echo ==========================================================
echo.
echo Server ise salinir...
cd /d "%~dp0"
start "" http://localhost:5050
node server.js
pause
