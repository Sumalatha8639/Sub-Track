<#
.SYNOPSIS
  Runs SubTrack's background jobs right now (auto-renewals, reminder emails, retries)
  instead of waiting for the 15-minute timer. Handy during a demo: the emails appear in
  Mailpit at http://localhost:8025.

.USAGE
  .\scripts\run-jobs.ps1
  With the demo profile, reminders go out at any hour; otherwise only around 9 AM in each
  user's timezone.
#>
$secret = if ($env:JOB_SECRET) { $env:JOB_SECRET } else { 'local-job-secret' }
try {
    $result = Invoke-RestMethod -Method Post -Uri 'http://localhost:8080/internal/jobs/run' `
        -Headers @{ 'X-Job-Secret' = $secret } -TimeoutSec 120
} catch {
    Write-Error "Could not run the jobs. Is the backend running on http://localhost:8080? ($($_.Exception.Message))"
    exit 1
}
if (-not $result.ran) {
    Write-Host 'A job run was already in progress; try again in a moment.'
    exit 0
}
Write-Host "Auto-renewed subscriptions: $($result.renewed)"
Write-Host "Reminder emails sent:       $($result.reminders.sent) (failed $($result.reminders.failed), already sent $($result.reminders.skipped))"
Write-Host "Retries sent:               $($result.retries.sent)"
Write-Host 'Open http://localhost:8025 to see the emails.'
