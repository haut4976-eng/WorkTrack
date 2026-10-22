$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$dll = Join-Path $root 'artifacts\backend-test-build\WorkTrack.Api.dll'
dotnet build (Join-Path $root 'backend\WorkTrack.Api\WorkTrack.Api.csproj') -p:UseAppHost=false -p:OutputPath=../../artifacts/backend-test-build/
if ($LASTEXITCODE -ne 0) { throw 'Build API test thất bại.' }
$testDir = Join-Path $root ('artifacts\api-tests\' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $testDir -Force | Out-Null
$db = Join-Path $testDir 'smoke.db'
$base = 'http://127.0.0.1:5011/api/v1'
$env:ASPNETCORE_ENVIRONMENT = 'Development'
$env:ASPNETCORE_URLS = 'http://127.0.0.1:5011'
$env:ConnectionStrings__Default = "Data Source=$db"
$env:WORKTRACK_DEMO_OTP = '1'
$server = Start-Process -FilePath 'dotnet' -ArgumentList "`"$dll`"" -WorkingDirectory (Join-Path $root 'backend\WorkTrack.Api') -WindowStyle Hidden -RedirectStandardOutput (Join-Path $testDir 'out.log') -RedirectStandardError (Join-Path $testDir 'error.log') -PassThru
try {
    $ready = $false
    for ($i=0; $i -lt 20; $i++) {
        Start-Sleep -Milliseconds 500
        try { if ((Invoke-RestMethod "$base/health" -TimeoutSec 1).status -eq 'ok') { $ready = $true; break } } catch { }
    }
    if (-not $ready) { throw "API test không khởi động. Xem $testDir\error.log" }
    function PostJson($url, $body, $access = $null) {
        $headers = @{}
        if ($access) { $headers.Authorization = "Bearer $access" }
        $json = $body | ConvertTo-Json -Depth 8 -Compress
        Invoke-RestMethod -Uri "$base$url" -Method Post -ContentType 'application/json; charset=utf-8' -Headers $headers -Body ([Text.Encoding]::UTF8.GetBytes($json))
    }
    function PutJson($url, $body, $access) {
        $json = $body | ConvertTo-Json -Depth 8 -Compress
        Invoke-RestMethod -Uri "$base$url" -Method Put -ContentType 'application/json; charset=utf-8' -Headers @{Authorization="Bearer $access"} -Body ([Text.Encoding]::UTF8.GetBytes($json))
    }
    function DeleteJson($url, $access) {
        Invoke-WebRequest -Uri "$base$url" -Method Delete -Headers @{Authorization="Bearer $access"} -UseBasicParsing | Out-Null
    }
    function Login($user, $password = 'DemoPass123!') {
        $login = PostJson '/auth/login' @{ username=$user; password=$password }
        if ($login.PSObject.Properties.Name -contains 'demoOtp') { throw 'API đăng nhập không được trả OTP trực tiếp.' }
        $demoOtp = (Invoke-RestMethod "$base/dev/otp?username=$user&purpose=LOGIN").code
        if (-not $demoOtp) { throw 'Hộp thư OTP demo chưa được bật.' }
        PostJson '/auth/verify-login-otp' @{username=$user;code=$demoOtp}
    }
    function ExpectHttp($expected, $action) {
        try { & $action | Out-Null; throw "Cần HTTP $expected nhưng request thành công." }
        catch [System.Net.WebException] {
            $actual = [int]$_.Exception.Response.StatusCode
            if ($actual -ne $expected) { throw "Cần HTTP $expected, nhận $actual." }
        }
    }
    $admin = Login 'admin'
    $employee = Login 'employee2'
    ExpectHttp 403 { Invoke-RestMethod "$base/employees" -Headers @{Authorization="Bearer $($employee.accessToken)"} }
    $department = PostJson '/departments' @{code='TEST';name='Phòng thử';description='Smoke test'} $admin.accessToken
    if (-not $department.id) { throw 'Tạo phòng ban thất bại.' }
    $department = PutJson "/departments/$($department.id)" @{code='TEST';name='Phòng thử cập nhật';description='Smoke test'} $admin.accessToken
    if ($department.name -ne 'Phòng thử cập nhật') { throw 'Sửa phòng ban thất bại.' }
    $shift = PostJson '/shifts' @{name='Ca kiểm thử';startTime='08:00:00';endTime='17:00:00';gracePeriodMinutes=10;description='Smoke test'} $admin.accessToken
    $shift = PutJson "/shifts/$($shift.id)" @{name='Ca kiểm thử mới';startTime='08:00:00';endTime='17:00:00';gracePeriodMinutes=10;description='Smoke test'} $admin.accessToken
    if ($shift.name -ne 'Ca kiểm thử mới') { throw 'Sửa ca thất bại.' }
    $newUser = 'smoke' + (Get-Random -Minimum 100000 -Maximum 999999)
    $newEmail = "$newUser@worktrack.local"
    $registration = PostJson '/auth/register' @{fullName='Nhân viên kiểm thử';username=$newUser;email=$newEmail;phone='';password='DemoPass123!';confirmPassword='DemoPass123!'}
    if ($registration.PSObject.Properties.Name -contains 'demoOtp') { throw 'API đăng ký không được trả OTP trực tiếp.' }
    $registerOtp = (Invoke-RestMethod "$base/dev/otp?username=$newUser&purpose=REGISTER").code
    PostJson '/auth/verify-registration' @{username=$newUser;code=$registerOtp} | Out-Null
    $forgot = PostJson '/auth/forgot-password' @{username=$newUser}
    if ($forgot.PSObject.Properties.Name -contains 'demoOtp') { throw 'API quên mật khẩu không được trả OTP trực tiếp.' }
    $resetOtp = (Invoke-RestMethod "$base/dev/otp?username=$newUser&purpose=RESET").code
    PostJson '/auth/reset-password' @{username=$newUser;code=$resetOtp;newPassword='ResetPass123!'} | Out-Null
    $profileSession = Login $newUser 'ResetPass123!'
    $profile = PutJson '/auth/me' @{fullName='Nhân viên kiểm thử mới';email=$newEmail;phone='0900000000'} $profileSession.accessToken
    if ($profile.fullName -ne 'Nhân viên kiểm thử mới' -or $profile.phone -ne '0900000000') { throw 'Cập nhật hồ sơ thất bại.' }
    PostJson '/auth/change-password' @{currentPassword='ResetPass123!';newPassword='DemoPass123!'} $profileSession.accessToken | Out-Null
    $newEmployee = PostJson '/employees' @{employeeCode='E900';fullName='Nhân viên kiểm thử';email=$newEmail;phone='';departmentId=$department.id;position='Tester';hireDate=[DateTime]::Today.ToString('yyyy-MM-dd');userId=$null} $admin.accessToken
    if (-not $newEmployee.id) { throw 'Gắn tài khoản nhân viên thất bại.' }
    PutJson "/users/$($profile.id)" @{role='EMPLOYEE';active=$false} $admin.accessToken | Out-Null
    ExpectHttp 401 { Invoke-RestMethod "$base/auth/me" -Headers @{Authorization="Bearer $($profileSession.accessToken)"} }
    PutJson "/users/$($profile.id)" @{role='EMPLOYEE';active=$true} $admin.accessToken | Out-Null
    $newSession = Login $newUser
    if ($newSession.role -ne 'EMPLOYEE') { throw 'Role nhân viên chưa được cập nhật.' }
    $futureDate = [DateTime]::Today.AddDays(2).ToString('yyyy-MM-dd')
    $assignment = PostJson '/shift-assignments' @{employeeId=$newEmployee.id;shiftId=$shift.id;workDate=$futureDate} $admin.accessToken
    ExpectHttp 409 { PostJson '/shift-assignments' @{employeeId=$newEmployee.id;shiftId=$shift.id;workDate=$futureDate} $admin.accessToken }
    DeleteJson "/shift-assignments/$($assignment.id)" $admin.accessToken
    $leave = PostJson '/leave-requests' @{startDate=$futureDate;endDate=$futureDate;reason='Kiểm thử'} $employee.accessToken
    $approved = PostJson "/leave-requests/$($leave.id)/approve" @{} $admin.accessToken
    if ($approved.status -ne 'APPROVED') { throw 'Duyệt đơn nghỉ thất bại.' }
    ExpectHttp 409 { PostJson "/leave-requests/$($leave.id)/approve" @{} $admin.accessToken }
    DeleteJson "/employees/$($newEmployee.id)" $admin.accessToken
    DeleteJson "/shifts/$($shift.id)" $admin.accessToken
    DeleteJson "/departments/$($department.id)" $admin.accessToken
    $office = (Invoke-RestMethod "$base/offices" -Headers @{Authorization="Bearer $($admin.accessToken)"})[0]
    $qr = PostJson "/qr/generate?officeId=$($office.id)" @{} $admin.accessToken
    $payload = @{ qrToken=$qr.token; latitude=$office.latitude; longitude=$office.longitude; accuracyMeters=5; locationTimestampUtc=[DateTime]::UtcNow.ToString('o') }
    $outside = $payload.Clone(); $outside.latitude = 0
    ExpectHttp 400 { PostJson '/attendance/check-in' $outside $employee.accessToken }
    $record = PostJson '/attendance/check-in' $payload $employee.accessToken
    if (-not $record.id) { throw 'Không tạo được bản ghi Check-in.' }
    ExpectHttp 400 { PostJson '/attendance/check-in' $payload $employee.accessToken }
    ExpectHttp 400 { PostJson '/attendance/check-out' $payload $employee.accessToken }
    $qr2 = PostJson "/qr/generate?officeId=$($office.id)" @{} $admin.accessToken
    $payload.qrToken = $qr2.token; $payload.locationTimestampUtc = [DateTime]::UtcNow.ToString('o')
    $checkedOut = PostJson '/attendance/check-out' $payload $employee.accessToken
    if (-not $checkedOut.checkOutUtc) { throw 'Check-out chưa ghi giờ ra.' }
    $today = Invoke-RestMethod "$base/attendance/today" -Headers @{Authorization="Bearer $($employee.accessToken)"}
    if ($today.record.id -ne $record.id -or -not $today.record.checkOutUtc) { throw 'Dashboard hôm nay không khớp bản ghi.' }
    $adminSummary = Invoke-RestMethod "$base/attendance/admin-summary" -Headers @{Authorization="Bearer $($admin.accessToken)"}
    if ($adminSummary.totalEmployees -lt 1 -or $adminSummary.checkedInToday -lt 1) { throw 'Dashboard Admin không tổng hợp đúng dữ liệu hôm nay.' }
    $rotated = PostJson '/auth/refresh' @{refreshToken=$employee.refreshToken}
    if (-not $rotated.accessToken) { throw 'Refresh token không hoạt động.' }
    ExpectHttp 401 { PostJson '/auth/refresh' @{refreshToken=$employee.refreshToken} }
    Write-Host 'PASS: OTP, quên/đổi mật khẩu, hồ sơ, quản lý người dùng, phân quyền, CRUD, phân ca, đơn nghỉ, GPS, QR một lần, check-in/out, dashboard và refresh rotation.'
    Write-Host "Log kiểm thử: $testDir"
}
finally { Stop-Process -Id $server.Id -ErrorAction SilentlyContinue }
