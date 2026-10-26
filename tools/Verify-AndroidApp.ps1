param([switch]$KeepRunning, [switch]$PrepareOnly, [switch]$BootOnly, [string]$ExpectedMessage)
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot\Enter-AndroidEnvironment.ps1"
$taskWorkspace = Split-Path $PSScriptRoot -Parent
$taskDiagnostics = Join-Path $taskWorkspace 'diagnostics'
$taskAvdName = 'PersonalApp_API36'
$taskSerial = 'emulator-5580'
$taskPackage = 'com.example.androidenvironmentcheck'
$taskApk = Join-Path $taskWorkspace 'android-environment-check\app\build\outputs\apk\debug\app-debug.apk'
$taskAdb = Join-Path $env:ANDROID_HOME 'platform-tools\adb.exe'
if (-not $PrepareOnly -and -not $BootOnly -and -not (Test-Path -LiteralPath $taskApk)) { throw 'Build the verification APK before running this script.' }
New-Item -ItemType Directory -Path $taskDiagnostics -Force | Out-Null
$taskAvdIni = Join-Path $env:USERPROFILE ".android\avd\$taskAvdName.ini"
if (-not (Test-Path -LiteralPath $taskAvdIni)) {
    'no' | & "$env:ANDROID_HOME\cmdline-tools\latest\bin\avdmanager.bat" create avd --name $taskAvdName --package 'system-images;android-36;google_apis;x86_64' --device 'pixel_6a'
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the dedicated verification emulator.' }
    $taskConfig = Join-Path $env:USERPROFILE ".android\avd\$taskAvdName.avd\config.ini"
    $taskConfigText = Get-Content -LiteralPath $taskConfig -Raw
    $taskSettings = [ordered]@{
        'hw.ramSize' = '2048'
        'hw.cpu.ncore' = '2'
        'hw.gpu.enabled' = 'yes'
        'hw.gpu.mode' = 'auto'
        'hw.lcd.width' = '720'
        'hw.lcd.height' = '1280'
        'hw.lcd.density' = '320'
        'disk.dataPartition.size' = '4G'
        'showDeviceFrame' = 'no'
        'skin.name' = '720x1280'
        'skin.dynamic' = 'yes'
    }
    foreach ($taskSetting in $taskSettings.GetEnumerator()) {
        $taskPattern = '(?m)^' + [regex]::Escape($taskSetting.Key) + '=.*$'
        $taskReplacement = $taskSetting.Key + '=' + $taskSetting.Value
        if ([regex]::IsMatch($taskConfigText, $taskPattern)) {
            $taskConfigText = [regex]::Replace($taskConfigText, $taskPattern, $taskReplacement)
        } else { $taskConfigText += "`n$taskReplacement`n" }
    }
    Set-Content -LiteralPath $taskConfig -Value $taskConfigText -Encoding utf8
}
if ($PrepareOnly) { Write-Output "Dedicated verification device prepared: $taskAvdName"; return }
$taskStartedEmulator = $false
try {
    $taskExistingDevices = (& $taskAdb devices | Out-String)
    if ($taskExistingDevices -notmatch "(?m)^$taskSerial\s+device") {
        if (Get-NetTCPConnection -LocalPort 5580 -State Listen -ErrorAction SilentlyContinue) { throw 'Verification emulator port 5580 is already in use.' }
        $taskProcess = Start-Process -FilePath "$env:ANDROID_HOME\emulator\emulator.exe" -ArgumentList @('-avd', $taskAvdName, '-port', '5580', '-no-window', '-no-audio', '-no-boot-anim', '-no-snapshot', '-gpu', 'auto') -WindowStyle Hidden -RedirectStandardOutput "$taskDiagnostics\emulator-stdout.log" -RedirectStandardError "$taskDiagnostics\emulator-stderr.log" -PassThru
        $taskStartedEmulator = $true
        $taskDeadline = (Get-Date).AddMinutes(6)
        $taskNextNotice = Get-Date
        do {
            $taskBootState = (& $taskAdb -s $taskSerial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
            if ($taskBootState -eq '1') { break }
            if ((Get-Date) -gt $taskNextNotice) {
                Write-Output 'Waiting for the dedicated Android emulator to finish booting...'
                $taskNextNotice = (Get-Date).AddSeconds(25)
            }
            if ($taskProcess.HasExited) { throw 'The emulator exited before Android finished booting. See emulator logs.' }
            Start-Sleep -Seconds 3
        } while ((Get-Date) -lt $taskDeadline)
        if ($taskBootState -ne '1') { throw 'Android emulator boot did not complete within six minutes.' }
    }
    $taskAvdIdentity = (& $taskAdb -s $taskSerial emu avd name | Select-Object -First 1).Trim()
    if ($taskAvdIdentity -ne $taskAvdName) { throw 'The connected emulator is not the dedicated verification device.' }
    if ($BootOnly) { Write-Output "Android emulator boot verified: $taskAvdName ($taskSerial)"; return }
    & $taskAdb -s $taskSerial shell input keyevent 82
    $taskInstall = (& $taskAdb -s $taskSerial install -r $taskApk 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $taskInstall -notmatch 'Success') { throw "APK installation failed: $taskInstall" }
    & $taskAdb -s $taskSerial shell am force-stop $taskPackage
    $taskLaunch = (& $taskAdb -s $taskSerial shell am start -W -n "$taskPackage/.MainActivity" 2>&1 | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $taskLaunch -notmatch 'Status: ok') { throw "Application launch failed: $taskLaunch" }
    Start-Sleep -Seconds 3
    & $taskAdb -s $taskSerial shell uiautomator dump /sdcard/android-env-window.xml | Out-Null
    & $taskAdb -s $taskSerial pull /sdcard/android-env-window.xml "$taskDiagnostics\ui-before.xml" | Out-Null
    [xml]$taskUiBefore = Get-Content -LiteralPath "$taskDiagnostics\ui-before.xml" -Raw -Encoding utf8
    $taskButton = $taskUiBefore.SelectSingleNode('//node[@text="验证点击"]')
    if (-not $taskButton) { throw 'The Compose verification button was not found in the running application.' }
    $taskBounds = [regex]::Match($taskButton.bounds, '\[(\d+),(\d+)\]\[(\d+),(\d+)\]')
    if (-not $taskBounds.Success) { throw 'Could not determine verification button coordinates.' }
    $taskTapX = [int](([int]$taskBounds.Groups[1].Value + [int]$taskBounds.Groups[3].Value) / 2)
    $taskTapY = [int](([int]$taskBounds.Groups[2].Value + [int]$taskBounds.Groups[4].Value) / 2)
    & $taskAdb -s $taskSerial shell input tap $taskTapX $taskTapY
    Start-Sleep -Seconds 1
    & $taskAdb -s $taskSerial shell uiautomator dump /sdcard/android-env-window.xml | Out-Null
    & $taskAdb -s $taskSerial pull /sdcard/android-env-window.xml "$taskDiagnostics\ui-after.xml" | Out-Null
    [xml]$taskUiAfter = Get-Content -LiteralPath "$taskDiagnostics\ui-after.xml" -Raw -Encoding utf8
    if (-not $taskUiAfter.SelectSingleNode('//node[@text="点击已响应 1 次"]')) { throw 'The Compose button did not update the displayed counter.' }
    if ($ExpectedMessage -and -not ($taskUiAfter.SelectNodes('//node[@text]') | Where-Object { $_.text -eq $ExpectedMessage })) {
        throw 'The installed application does not display the expected updated message.'
    }
    & $taskAdb -s $taskSerial shell screencap -p /sdcard/android-env-screen.png
    & $taskAdb -s $taskSerial pull /sdcard/android-env-screen.png "$taskDiagnostics\app-running.png" | Out-Null
    $taskResults = [PSCustomObject]@{
        VerifiedAt = (Get-Date).ToString('o')
        Emulator = $taskAvdName
        Serial = $taskSerial
        AndroidApiLevel = (& $taskAdb -s $taskSerial shell getprop ro.build.version.sdk | Out-String).Trim()
        Apk = $taskApk
        Installed = $true
        LaunchSuccessful = $true
        ComposeButtonInteractionPassed = $true
        UpdateMessageVerified = if ($ExpectedMessage) { $true } else { $null }
        InstallOutput = $taskInstall
        LaunchOutput = $taskLaunch
        Screenshot = "$taskDiagnostics\app-running.png"
        ScreenText = @($taskUiAfter.SelectNodes('//node[@text!=""]') | ForEach-Object { $_.text })
    }
    $taskResults | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath "$taskDiagnostics\app-verification.json" -Encoding utf8
    $taskResults | ConvertTo-Json -Depth 5
} finally {
    if ($taskStartedEmulator -and -not $KeepRunning) {
        & $taskAdb -s $taskSerial emu kill 2>$null | Out-Null
        Write-Output 'Verification emulator shut down to release memory.'
    }
}
