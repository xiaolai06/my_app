param([Parameter(Mandatory = $true)][string]$BackupDirectory)
$ErrorActionPreference = 'Stop'
$taskSnapshot = Join-Path $BackupDirectory 'environment-before.json'
$taskSaved = Get-Content -LiteralPath $taskSnapshot -Raw | ConvertFrom-Json
foreach ($taskEntry in $taskSaved.Environment) {
    [Environment]::SetEnvironmentVariable($taskEntry.Name, $taskEntry.Value, 'User')
}
$taskSavedAvd = Join-Path $BackupDirectory 'Pixel_5.ini'
$taskOriginalAvd = Join-Path $env:USERPROFILE '.android\avd\Pixel_5.ini'
if ((Test-Path -LiteralPath $taskSavedAvd) -and -not (Test-Path -LiteralPath $taskOriginalAvd)) {
    Copy-Item -LiteralPath $taskSavedAvd -Destination $taskOriginalAvd
}
Write-Output 'Previous user environment restored. Reopen applications to reload it. Installed SDK tools are retained.'

