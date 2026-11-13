# Registers the six o'clock task for refresh-dataset.ps1. Run once; running it again replaces it.
#
# StartWhenAvailable: if the PC was off at six, the run happens as soon as it is back on.
# WakeToRun: a sleeping PC wakes for it (a PC that is shut down does not).
$script = Join-Path $PSScriptRoot "refresh-dataset.ps1"
$action = New-ScheduledTaskAction -Execute "powershell.exe" `
    -Argument "-NoProfile -WindowStyle Hidden -ExecutionPolicy Bypass -File `"$script`""
$trigger = New-ScheduledTaskTrigger -Daily -At "06:00"
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -WakeToRun `
    -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -ExecutionTimeLimit (New-TimeSpan -Minutes 5)
Register-ScheduledTask -TaskName "OW Companion - dataset alle 6" -Action $action `
    -Trigger $trigger -Settings $settings -Force `
    -Description "Avvia il workflow Refresh dataset di bdbais/ow-companion alle 6:00." | Out-Null
Get-ScheduledTask -TaskName "OW Companion - dataset alle 6" | Get-ScheduledTaskInfo | Select-Object NextRunTime
