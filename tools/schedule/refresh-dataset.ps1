# Starts the "Refresh dataset" workflow on GitHub at six in the morning.
#
# GitHub's own cron is the fallback, not the clock: in November 2026 its daily runs started
# between 11:00 and 14:00 UTC, six to eight hours late. This runs from Windows Task Scheduler
# on the developer's PC (registered by register-task.ps1) with the GitHub CLI's existing
# login, so no token is stored anywhere new.
$log = Join-Path $PSScriptRoot "refresh-dataset.log"
$stamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$out = & "C:\Program Files\GitHub CLI\gh.exe" workflow run refresh-dataset.yml -R bdbais/ow-companion --ref master 2>&1
"$stamp exit=$LASTEXITCODE $out" | Add-Content -Path $log -Encoding utf8
# Keep the log to its last hundred lines.
$lines = Get-Content $log -Encoding utf8
if ($lines.Count -gt 100) { $lines | Select-Object -Last 100 | Set-Content $log -Encoding utf8 }
