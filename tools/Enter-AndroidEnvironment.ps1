param(
    [string]$SdkPath = 'E:\AndroidSDK',
    [string]$JdkPath = 'E:\Android\jbr'
)
$ErrorActionPreference = 'Stop'
foreach ($taskRequiredPath in @("$JdkPath\bin\java.exe", "$SdkPath\platform-tools\adb.exe")) {
    if (-not (Test-Path -LiteralPath $taskRequiredPath)) { throw "Required tool missing: $taskRequiredPath" }
}
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
$taskEntries = @("$JdkPath\bin", "$SdkPath\platform-tools", "$SdkPath\emulator", "$SdkPath\cmdline-tools\latest\bin")
$env:Path = (($taskEntries + ($env:Path -split ';')) | Where-Object { $_ } | Select-Object -Unique) -join ';'
Write-Output "Android environment: JDK=$JdkPath; SDK=$SdkPath"

