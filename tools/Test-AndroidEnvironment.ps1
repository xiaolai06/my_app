$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\Enter-AndroidEnvironment.ps1"
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskDiagnostics = Join-Path $taskWorkspace 'diagnostics'
New-Item -ItemType Directory -Path $taskDiagnostics -Force | Out-Null
$taskJava = (& "$env:JAVA_HOME\bin\java.exe" -version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'JDK verification failed.' }
$taskSdkManager = (& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --version 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'SDK command-line tools verification failed.' }
$taskInstalled = (& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --sdk_root=$env:ANDROID_HOME --list_installed 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'SDK package enumeration failed.' }
$taskInstalled | Set-Content -LiteralPath "$taskDiagnostics\sdk-installed.txt" -Encoding utf8
$taskDevices = (& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'ADB verification failed.' }
$taskAcceleration = (& "$env:ANDROID_HOME\emulator\emulator.exe" -accel-check 2>&1 | Out-String).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Emulator hardware acceleration verification failed.' }
Push-Location -LiteralPath "$taskWorkspace\android-environment-check"
try {
    $taskGradle = (& '.\gradlew.bat' --version 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0) { throw 'Project Gradle wrapper verification failed.' }
} finally { Pop-Location }
$taskResults = [PSCustomObject]@{
    VerifiedAt = (Get-Date).ToString('o')
    UserJavaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
    UserAndroidHome = [Environment]::GetEnvironmentVariable('ANDROID_HOME', 'User')
    EffectiveJavaCommand = (Get-Command java).Source
    JavaVersion = $taskJava
    SdkManagerVersion = $taskSdkManager
    GradleVersion = $taskGradle
    ConnectedDevices = $taskDevices
    EmulatorAcceleration = $taskAcceleration
    AvailableVirtualDevices = @(& "$env:ANDROID_HOME\emulator\emulator.exe" -list-avds)
}
$taskResults | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$taskDiagnostics\environment-verification.json" -Encoding utf8
$taskResults | ConvertTo-Json -Depth 5

