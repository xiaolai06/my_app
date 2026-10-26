param(
    [string]$SdkPath = 'E:\AndroidSDK',
    [string]$JdkPath = 'E:\Android\jbr'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskDiagnostics = Join-Path $taskWorkspace 'diagnostics'
$taskStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$taskBackup = Join-Path $taskDiagnostics "backups\$taskStamp"
New-Item -ItemType Directory -Path $taskBackup -Force | Out-Null
if (-not (Test-Path -LiteralPath "$JdkPath\bin\java.exe")) { throw 'The selected JDK does not exist.' }
if (-not (Test-Path -LiteralPath "$SdkPath\platform-tools\adb.exe")) { throw 'The selected SDK does not exist.' }
$taskEnvironmentBefore = foreach ($taskName in @('JAVA_HOME', 'ANDROID_HOME', 'ANDROID_SDK_ROOT', 'Path')) {
    [PSCustomObject]@{ Name = $taskName; Value = [Environment]::GetEnvironmentVariable($taskName, 'User') }
}
[PSCustomObject]@{
    RecordedAt = (Get-Date).ToString('o')
    Environment = @($taskEnvironmentBefore)
    SdkPath = $SdkPath
    JdkPath = $JdkPath
} | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$taskBackup\environment-before.json" -Encoding utf8
Write-Output "Configuration backup: $taskBackup"

# Only user variables are changed. The machine PATH and existing Oracle JDK remain intact.
[Environment]::SetEnvironmentVariable('JAVA_HOME', $JdkPath, 'User')
[Environment]::SetEnvironmentVariable('ANDROID_HOME', $SdkPath, 'User')
$taskOldSdkRoot = [Environment]::GetEnvironmentVariable('ANDROID_SDK_ROOT', 'User')
if ($taskOldSdkRoot) { [Environment]::SetEnvironmentVariable('ANDROID_SDK_ROOT', $SdkPath, 'User') }
$taskUserPathBefore = [Environment]::GetEnvironmentVariable('Path', 'User')
$taskExistingEntries = @($taskUserPathBefore -split ';' | Where-Object { $_ -and $_ -ne '%JAVA_HOME%bin' })
$taskAddedEntries = @("$JdkPath\bin", "$SdkPath\platform-tools", "$SdkPath\emulator", "$SdkPath\cmdline-tools\latest\bin")
$taskUserPathAfter = (($taskAddedEntries + $taskExistingEntries) | Select-Object -Unique) -join ';'
[Environment]::SetEnvironmentVariable('Path', $taskUserPathAfter, 'User')
& "$PSScriptRoot\Enter-AndroidEnvironment.ps1" -SdkPath $SdkPath -JdkPath $JdkPath

if (-not ('AndroidEnvironmentBroadcast' -as [type])) {
    Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class AndroidEnvironmentBroadcast {
    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern IntPtr SendMessageTimeout(IntPtr window, uint message, UIntPtr wParam, string text, uint flags, uint timeout, out UIntPtr result);
}
'@
}
$taskBroadcastResult = [UIntPtr]::Zero
[void][AndroidEnvironmentBroadcast]::SendMessageTimeout([IntPtr]0xffff, 0x001A, [UIntPtr]::Zero, 'Environment', 2, 3000, [ref]$taskBroadcastResult)
Write-Output 'User JAVA_HOME, ANDROID_HOME and Android tool paths configured.'

$taskSdkManager = Join-Path $SdkPath 'cmdline-tools\latest\bin\sdkmanager.bat'
if (-not (Test-Path -LiteralPath $taskSdkManager)) {
    $taskDownloadDir = Join-Path $taskDiagnostics 'downloads'
    New-Item -ItemType Directory -Path $taskDownloadDir -Force | Out-Null
    $taskZip = Join-Path $taskDownloadDir 'commandlinetools-win-15859902_latest.zip'
    $taskExpectedHash = '90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a'
    if (-not (Test-Path -LiteralPath $taskZip)) {
        Write-Output 'Downloading the official Android command-line tools package.'
        Invoke-WebRequest -Uri 'https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip' -OutFile $taskZip -TimeoutSec 300
    }
    $taskActualHash = (Get-FileHash -LiteralPath $taskZip -Algorithm SHA256).Hash
    if ($taskActualHash -ne $taskExpectedHash) { throw "Command-line tools checksum mismatch: $taskActualHash" }
    $taskStage = Join-Path $taskDiagnostics "staging\cmdline-$taskStamp"
    New-Item -ItemType Directory -Path $taskStage -Force | Out-Null
    [System.IO.Compression.ZipFile]::ExtractToDirectory($taskZip, $taskStage)
    $taskTarget = Join-Path $SdkPath 'cmdline-tools\latest'
    if (Test-Path -LiteralPath $taskTarget) { throw "Install destination already exists; review before changing: $taskTarget" }
    New-Item -ItemType Directory -Path (Split-Path $taskTarget -Parent) -Force | Out-Null
    Copy-Item -LiteralPath (Join-Path $taskStage 'cmdline-tools') -Destination $taskTarget -Recurse
    Write-Output 'Official command-line tools installed; SHA-256 verified.'
}

# Quarantine only the confirmed orphaned Pixel_5 pointer. No virtual disk is removed.
$taskAvdRoot = Join-Path $env:USERPROFILE '.android\avd'
$taskStaleIni = Join-Path $taskAvdRoot 'Pixel_5.ini'
$taskExpectedMissingAvd = Join-Path $taskAvdRoot 'Pixel_5.avd'
if ((Test-Path -LiteralPath $taskStaleIni) -and -not (Test-Path -LiteralPath $taskExpectedMissingAvd)) {
    $taskAvdPathLine = Get-Content -LiteralPath $taskStaleIni | Where-Object { $_ -match '^path=' } | Select-Object -First 1
    if ($taskAvdPathLine -and [IO.Path]::GetFullPath($taskAvdPathLine.Substring(5)) -eq [IO.Path]::GetFullPath($taskExpectedMissingAvd)) {
        Move-Item -LiteralPath $taskStaleIni -Destination (Join-Path $taskBackup 'Pixel_5.ini')
        Write-Output 'Orphaned Pixel_5 pointer moved to backup; Pixel_6a preserved.'
    }
}
[PSCustomObject]@{
    ConfiguredAt = (Get-Date).ToString('o')
    BackupDirectory = $taskBackup
    JdkPath = $JdkPath
    SdkPath = $SdkPath
    CommandLineToolsPresent = (Test-Path -LiteralPath $taskSdkManager)
    EnvironmentScope = 'User'
} | ConvertTo-Json | Set-Content -LiteralPath "$taskDiagnostics\environment-configuration.json" -Encoding utf8
Write-Output 'Android environment configuration complete.'

