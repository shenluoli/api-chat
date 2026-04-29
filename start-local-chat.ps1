$ErrorActionPreference = "Stop"

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$RuntimeFile = Join-Path $Root ".local-chat-server.json"
$LogFile = Join-Path $Root "启动日志.txt"
$DefaultUrl = "http://127.0.0.1:8787"

function Write-Log {
  param([string]$Message)

  $timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
  Add-Content -LiteralPath $LogFile -Value "[$timestamp] $Message" -Encoding UTF8
}

function Open-ChatUrl {
  param([string]$Url)

  Write-Log "Opening $Url"

  try {
    Start-Process -FilePath "explorer.exe" -ArgumentList $Url
    return
  } catch {
    Write-Log "explorer.exe open failed: $($_.Exception.Message)"
  }

  Start-Process -FilePath "rundll32.exe" -ArgumentList "url.dll,FileProtocolHandler", $Url
}

function Test-ChatServer {
  param([string]$Url)

  try {
    $health = Invoke-RestMethod -Uri "$Url/api/health" -Method Get -TimeoutSec 2
    return $health.ok -eq $true
  } catch {
    return $false
  }
}

function Get-RuntimeUrl {
  if (-not (Test-Path -LiteralPath $RuntimeFile)) {
    return $null
  }

  try {
    $runtime = Get-Content -Raw -LiteralPath $RuntimeFile | ConvertFrom-Json
    return $runtime.url
  } catch {
    return $null
  }
}

$runtimeUrl = Get-RuntimeUrl
if ($runtimeUrl -and (Test-ChatServer $runtimeUrl)) {
  Write-Log "Server already running at $runtimeUrl"
  Open-ChatUrl $runtimeUrl
  exit 0
}

Set-Location -LiteralPath $Root
Write-Log "Starting server from $Root"
Start-Process -FilePath "node.exe" -ArgumentList "server.js" -WorkingDirectory $Root -WindowStyle Hidden

$url = $DefaultUrl
for ($i = 0; $i -lt 50; $i++) {
  Start-Sleep -Milliseconds 200
  $runtimeUrl = Get-RuntimeUrl
  if ($runtimeUrl) {
    $url = $runtimeUrl
  }

  if (Test-ChatServer $url) {
    Write-Log "Server ready at $url"
    Open-ChatUrl $url
    exit 0
  }
}

Write-Log "Server did not start in time."
Write-Host "Local API Chat did not start in time."
Write-Host "Run this command for details: node server.js"
Read-Host "Press Enter to close"
exit 1
