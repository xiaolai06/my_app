[CmdletBinding(PositionalBinding = $false)]
param(
    [string]$ProjectPath = (Join-Path (Split-Path $PSScriptRoot -Parent) 'daily-life-android'),
    [string]$SdkPath,
    [string]$JdkPath,
    [Parameter(Position = 0, ValueFromRemainingArguments = $true)][string[]]$BuildArguments
)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\Enter-AndroidEnvironment.ps1" -SdkPath $SdkPath -JdkPath $JdkPath
$taskProject = (Resolve-Path -LiteralPath $ProjectPath).Path
if (-not (Test-Path -LiteralPath (Join-Path $taskProject 'gradlew.bat'))) { throw "不是 Android 工程：$taskProject" }
if (-not $BuildArguments) { $BuildArguments = @('assembleDebug', '--console=plain') }
Push-Location -LiteralPath $taskProject
try {
    & '.\gradlew.bat' @BuildArguments
    if ($LASTEXITCODE -ne 0) { throw "Android build failed with exit code $LASTEXITCODE" }
} finally { Pop-Location }

