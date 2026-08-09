# Launches the real game, drives it through four states, captures each, then stops it.
# All in one process so the game never has to survive a tool-call boundary.
# The window is deliberately never moved or resized - doing so terminates the
# JavaFX stage - so the capture height is clamped to the visible desktop instead.
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

Add-Type @"
using System;
using System.Runtime.InteropServices;
public class W {
    [DllImport("user32.dll")] public static extern bool SetProcessDPIAware();
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
    [DllImport("user32.dll")] public static extern bool GetClientRect(IntPtr h, out RECT r);
    [DllImport("user32.dll")] public static extern bool ClientToScreen(IntPtr h, ref POINT p);
    [StructLayout(LayoutKind.Sequential)] public struct RECT { public int L, T, R, B; }
    [StructLayout(LayoutKind.Sequential)] public struct POINT { public int X, Y; }
}
"@
[W]::SetProcessDPIAware() | Out-Null

$root = "C:\Users\moval\Desktop\Game-Poject\full\AsteroidsFX-master"
$fig  = "$root\docs\report-figures"
$screenH = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea.Height
$screenW = [System.Windows.Forms.Screen]::PrimaryScreen.WorkingArea.Width
Write-Output "working area ${screenW}x${screenH}"

$p = Start-Process -FilePath "java" `
    -ArgumentList "--module-path=mods-mvn", "--module=Core/dk.sdu.mmmi.cbse.main.Main" `
    -WorkingDirectory $root -PassThru
Write-Output "LAUNCHED PID $($p.Id)"

$h = [IntPtr]::Zero
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Milliseconds 500
    $q = Get-Process -Id $p.Id -ErrorAction SilentlyContinue
    if ($null -eq $q) { Write-Output "PROCESS DIED while starting"; exit 1 }
    $q.Refresh()
    if ($q.MainWindowTitle -eq "ASTEROIDS" -and $q.MainWindowHandle -ne [IntPtr]::Zero) {
        $h = $q.MainWindowHandle
        Write-Output "WINDOW READY"
        break
    }
}
if ($h -eq [IntPtr]::Zero) { Write-Output "NO WINDOW"; Stop-Process -Id $p.Id -Force; exit 1 }
Start-Sleep -Milliseconds 800

function Grab([string]$out) {
    [W]::SetForegroundWindow($h) | Out-Null
    Start-Sleep -Milliseconds 400
    $r = New-Object W+RECT
    [W]::GetClientRect($h, [ref]$r) | Out-Null
    $tl = New-Object W+POINT; $tl.X = 0; $tl.Y = 0
    [W]::ClientToScreen($h, [ref]$tl) | Out-Null
    $w = $r.R - $r.L
    $ht = $r.B - $r.T
    if ($w -le 0 -or $ht -le 0) { Write-Output "SKIP $(Split-Path $out -Leaf) - window gone"; return }
    # never read past the visible desktop, or the taskbar bleeds into the shot
    if (($tl.Y + $ht) -gt $screenH) { $ht = $screenH - $tl.Y }
    if (($tl.X + $w) -gt $screenW) { $w = $screenW - $tl.X }
    $bmp = New-Object System.Drawing.Bitmap($w, $ht)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CopyFromScreen($tl.X, $tl.Y, 0, 0, (New-Object System.Drawing.Size($w, $ht)))
    $bmp.Save($out, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Output "CAPTURED $(Split-Path $out -Leaf) ${w}x${ht}"
}

function Key([string]$k, [int]$ms = 350) {
    [W]::SetForegroundWindow($h) | Out-Null
    Start-Sleep -Milliseconds 120
    [System.Windows.Forms.SendKeys]::SendWait($k)
    Start-Sleep -Milliseconds $ms
}

Grab "$fig\shot04-startmenu.png"

Key " " 900
Key "{UP}" 300
Key " " 250
Key "{UP}" 300
Key " " 250
Key "{RIGHT}" 250
Key " " 450
Grab "$fig\shot05-gameplay.png"

Key "p" 800
Key "h" 800
Grab "$fig\shot06-pausemenu.png"

Key "h" 400
Key "p" 800
Key "3" 800
Key " " 250
Key " " 250
Key " " 550
Grab "$fig\shot07-weapon-uninstalled.png"

Key "3" 800
Key " " 250
Key " " 550
Grab "$fig\shot08-weapon-reinstalled.png"

Start-Sleep -Milliseconds 400
$still = Get-Process -Id $p.Id -ErrorAction SilentlyContinue
if ($still) { Stop-Process -Id $p.Id -Force; Write-Output "game stopped" } else { Write-Output "game already exited" }
Write-Output "SEQUENCE DONE"
