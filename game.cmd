@echo off
rem Sends one plugin command to the running game and prints its reply.
rem
rem   game plugin list
rem   game plugin disable Enemy
rem   game plugin unload Enemy
rem   game plugin load Enemy
rem   game plugin enable Enemy
rem
rem The game must already be running; this script never starts, stops or
rem restarts it.

setlocal
if "%~1"=="" (
    echo usage: game plugin ^<list^|load^|enable^|disable^|unload^|reload^> [name]
    exit /b 2
)
if "%ASTEROIDS_PLUGIN_PORT%"=="" set "ASTEROIDS_PLUGIN_PORT=5599"
set "ASTEROIDS_PLUGIN_CMD=%*"

powershell -NoProfile -Command "$p=[int]$env:ASTEROIDS_PLUGIN_PORT; try { $t = New-Object Net.Sockets.TcpClient('127.0.0.1', $p) } catch { Write-Error ('cannot reach the game on 127.0.0.1:' + $p + ' - is it running?'); exit 1 }; $s = $t.GetStream(); $w = New-Object IO.StreamWriter($s); $w.WriteLine($env:ASTEROIDS_PLUGIN_CMD); $w.Flush(); $r = New-Object IO.StreamReader($s); Write-Host $r.ReadToEnd().TrimEnd(); $t.Close()"

endlocal
