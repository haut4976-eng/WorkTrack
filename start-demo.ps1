$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendProject = Join-Path $projectRoot 'backend\WorkTrack.Api\WorkTrack.Api.csproj'
$backendFolder = Join-Path $projectRoot 'backend\WorkTrack.Api'
$backendDll = Join-Path $projectRoot 'artifacts\backend-build\WorkTrack.Api.dll'
$androidFolder = Join-Path $projectRoot 'android'
$apk = Join-Path $androidFolder 'app\build\outputs\apk\debug\app-debug.apk'
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
$emulator = Join-Path $sdk 'emulator\emulator.exe'
$logs = Join-Path $projectRoot 'artifacts\demo-logs'
New-Item -ItemType Directory -Path $logs -Force | Out-Null
if (-not (Test-Path $adb)) { throw "Không tìm thấy Android SDK tại $sdk. Hãy cài Android Studio SDK hoặc đặt ANDROID_HOME." }

# Luôn chạy đúng bản vừa build. Chỉ dừng tiến trình nếu nó đang chạy chính DLL của dự án này.
$listener = Get-NetTCPConnection -LocalPort 5000 -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
if ($listener) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if ($process.CommandLine -notlike "*$backendDll*") { throw "Cổng 5000 đang được ứng dụng khác sử dụng (PID $($listener.OwningProcess))." }
    Stop-Process -Id $listener.OwningProcess -Force
    Start-Sleep -Milliseconds 500
}
Write-Host '1/4 Xây dựng Backend...'
dotnet build $backendProject -p:UseAppHost=false -p:OutputPath=../../artifacts/backend-build/
if ($LASTEXITCODE -ne 0) { throw 'Build Backend thất bại.' }

Write-Host '2/4 Khởi động Backend với OTP demo tự xác minh trong ứng dụng...'
$env:ASPNETCORE_ENVIRONMENT = 'Development'
$env:WORKTRACK_DEMO_OTP = '1'
Start-Process -FilePath 'dotnet' -ArgumentList "`"$backendDll`"" -WorkingDirectory $backendFolder -WindowStyle Hidden -RedirectStandardOutput (Join-Path $logs 'backend-out.log') -RedirectStandardError (Join-Path $logs 'backend-error.log') | Out-Null
$ready = $false
for ($i=0; $i -lt 30; $i++) {
    Start-Sleep -Seconds 1
    try { if ((Invoke-RestMethod 'http://127.0.0.1:5000/api/v1/health' -TimeoutSec 1).status -eq 'ok') { $ready = $true; break } } catch { }
}
if (-not $ready) { throw "Backend không khởi động. Xem $logs\backend-error.log" }

Write-Host '3/4 Xây dựng APK...'
$env:ANDROID_HOME = $sdk
Push-Location $androidFolder
try { & '.\gradlew.bat' assembleDebug; if ($LASTEXITCODE -ne 0) { throw 'Build Android thất bại.' } }
finally { Pop-Location }
if (-not (Test-Path $apk)) { throw 'Không tìm thấy APK sau khi build.' }

$devices = & $adb devices
if (-not ($devices | Select-String 'emulator-\d+\s+device')) {
    if (-not (Test-Path $emulator)) { throw 'Chưa có Emulator đang chạy và không tìm thấy emulator.exe.' }
    $avds = & $emulator -list-avds
    $avd = $avds | Select-Object -First 1
    if (-not $avd) { throw 'Chưa có AVD. Hãy tạo một Emulator trong Android Studio.' }
    Write-Host "Khởi động Emulator $avd..."
    Start-Process -FilePath $emulator -ArgumentList @('-avd', $avd) -WindowStyle Normal | Out-Null
    $connected = $false
    for ($i=0; $i -lt 60; $i++) {
        if ((& $adb devices) -match 'emulator-\d+\s+device') { $connected = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $connected) { throw 'Emulator không kết nối ADB sau 2 phút. Hãy đóng Emulator bị treo rồi chạy lại START_DEMO.cmd.' }
    for ($i=0; $i -lt 90; $i++) { if ((& $adb shell getprop sys.boot_completed).Trim() -eq '1') { break }; Start-Sleep -Seconds 2 }
}
Write-Host '4/4 Cài đặt và mở WorkTrack...'
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) { throw 'Cài APK thất bại.' }
& $adb shell am start -n com.worktrack.app/.MainActivity
if ($LASTEXITCODE -ne 0) { throw 'Không mở được ứng dụng.' }
Write-Host 'Đã mở WorkTrack. Demo: admin / employee1 / employee2, mật khẩu DemoPass123!.'
Write-Host 'Để chấm công: Admin hiển thị QR trên thiết bị thứ hai; Employee bấm Check-in và quét QR. Bật GPS tại vị trí văn phòng và đăng ký vân tay trên Emulator.'
