param(
    [string]$SdkPath,
    [string]$JdkPath
)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
if (-not $SdkPath) {
    $SdkPath = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT,
        (Join-Path $env:USERPROFILE 'AndroidDev\Sdk'),
        (Join-Path $env:LOCALAPPDATA 'Android\Sdk'), 'E:\AndroidSDK') |
        Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'platform-tools\adb.exe')) } |
        Select-Object -First 1
}
function Get-TaskJavaMajor([string]$TaskJdk) {
    if (-not $TaskJdk) { return 0 }
    $taskRelease = Join-Path $TaskJdk 'release'
    if (-not (Test-Path -LiteralPath $taskRelease)) { return 0 }
    $taskVersion = [regex]::Match((Get-Content -Raw -LiteralPath $taskRelease), 'JAVA_VERSION="(\d+)')
    if ($taskVersion.Success) { return [int]$taskVersion.Groups[1].Value }
    return 0
}
if (-not $JdkPath) {
    $taskPortable = Join-Path $taskRoot 'android-setup\jdk21'
    $taskCandidates = @($env:JAVA_HOME)
    if (Test-Path -LiteralPath $taskPortable) {
        $taskCandidates += Get-ChildItem -LiteralPath $taskPortable -Directory | Select-Object -ExpandProperty FullName
    }
    $taskCandidates += @((Join-Path $env:USERPROFILE 'AndroidDev\AndroidStudio\jbr'), 'E:\Android\jbr')
    $JdkPath = $taskCandidates | Where-Object { (Get-TaskJavaMajor $_) -in 17..23 } | Select-Object -First 1
}
if (-not $SdkPath -or -not $JdkPath) {
    throw '未找到兼容环境。请指定 -SdkPath 和 -JdkPath；本项目 Gradle 8.13 请使用 Java 17 至 23，推荐 Java 21。'
}
if ((Get-TaskJavaMajor $JdkPath) -notin 17..23) {
    throw '此 JDK 与项目固定的 Gradle 8.13 不兼容，请指定 Java 17 至 23（推荐 21）。'
}
foreach ($taskRequiredPath in @("$JdkPath\bin\java.exe", "$SdkPath\platform-tools\adb.exe")) {
    if (-not (Test-Path -LiteralPath $taskRequiredPath)) { throw "Required tool missing: $taskRequiredPath" }
}
$env:JAVA_HOME = $JdkPath
$env:ANDROID_HOME = $SdkPath
$taskEntries = @("$JdkPath\bin", "$SdkPath\platform-tools", "$SdkPath\emulator", "$SdkPath\cmdline-tools\latest\bin")
$env:Path = (($taskEntries + ($env:Path -split ';')) | Where-Object { $_ } | Select-Object -Unique) -join ';'
Write-Output "Android environment: JDK=$JdkPath; SDK=$SdkPath"

