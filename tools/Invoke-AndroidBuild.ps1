param([Parameter(ValueFromRemainingArguments = $true)][string[]]$BuildArguments)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\Enter-AndroidEnvironment.ps1"
$taskProject = Join-Path (Split-Path $PSScriptRoot -Parent) 'android-environment-check'
if (-not $BuildArguments) { $BuildArguments = @('assembleDebug', '--console=plain') }
Push-Location -LiteralPath $taskProject
try {
    & '.\gradlew.bat' @BuildArguments
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

