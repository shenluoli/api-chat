$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
$isAdmin = $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
  Start-Process -FilePath "powershell.exe" -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
  exit 0
}

$ruleName = "Local API Chat 8787"

netsh advfirewall firewall delete rule name="$ruleName" | Out-Null
netsh advfirewall firewall add rule name="$ruleName" dir=in action=allow protocol=TCP localport=8787 remoteip=localsubnet profile=private,public | Out-Host

Write-Host ""
Write-Host "Firewall rule added: local subnet can access TCP 8787."
Write-Host "Try this on your phone: http://192.168.18.6:8787"
Write-Host ""
Read-Host "Press Enter to close"
