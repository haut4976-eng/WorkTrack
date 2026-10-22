package com.worktrack.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.google.gson.JsonParser

private val blue = Color(0xFF2563EB)
private val vietnamTime = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"))
internal fun formatUtc(value: String): String = runCatching {
    val instant = runCatching { Instant.parse(value) }.getOrElse { LocalDateTime.parse(value).toInstant(ZoneOffset.UTC) }
    vietnamTime.format(instant)
}.getOrDefault(value.take(16).replace('T', ' '))

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = lightColorScheme(primary = blue)) { WorkTrackScreen() } }
    }
}

@Composable
private fun WorkTrackScreen() {
    val activity = LocalContext.current as FragmentActivity
    val secureStore = remember(activity) { SecureSessionStore(activity) }
    val restored = remember { secureStore.load() }
    var page by remember { mutableStateOf(if (restored == null) "login" else "dashboard") }
    var section by remember { mutableStateOf("Tổng quan") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var otp by remember { mutableStateOf("") }
    var session by remember { mutableStateOf(restored) }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var departments by remember { mutableStateOf<List<Department>>(emptyList()) }
    var today by remember { mutableStateOf<Today?>(null) }
    var summary by remember { mutableStateOf<Summary?>(null) }
    var adminSummary by remember { mutableStateOf<AdminSummary?>(null) }
    var history by remember { mutableStateOf<List<Attendance>>(emptyList()) }
    var shiftList by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var offices by remember { mutableStateOf<List<Office>>(emptyList()) }
    var issuedQr by remember { mutableStateOf<QrToken?>(null) }
    var step by remember { mutableStateOf("idle") }
    var checkingIn by remember { mutableStateOf(true) }
    var pendingLocation by remember { mutableStateOf<Location?>(null) }
    val scope = rememberCoroutineScope()
    val api = remember { ApiFactory({ secureStore.load()?.accessToken ?: session?.accessToken }, secureStore).api }
    fun run(action: suspend () -> Unit) {
        scope.launch {
            loading = true
            message = ""
            try { action() } catch (_: Exception) { message = "Không kết nối được Backend. Kiểm tra cổng 5000 và địa chỉ 10.0.2.2." }
            finally { loading = false }
        }
    }
    fun loadDashboard() = run {
        when (session?.role) {
            "ADMIN" -> {
                val er = api.employees(); val dr = api.departments(); val or = api.offices(); val ar = api.adminSummary()
                if (er.isSuccessful && dr.isSuccessful && or.isSuccessful && ar.isSuccessful) { employees = er.body().orEmpty(); departments = dr.body().orEmpty(); offices = or.body().orEmpty(); adminSummary = ar.body() }
                else message = "Không tải được dữ liệu quản trị."
            }
            "EMPLOYEE" -> {
                val tr = api.today(); val sr = api.summary(); val hr = api.history(); val sh = api.shifts()
                if (tr.isSuccessful && sr.isSuccessful && hr.isSuccessful && sh.isSuccessful) { today = tr.body(); summary = sr.body(); history = hr.body().orEmpty(); shiftList = sh.body().orEmpty() }
                else message = "Không tải được dữ liệu chấm công."
            }
            "CUSTOMER" -> {
                val result = api.me()
                if (!result.isSuccessful) message = "Không tải được hồ sơ."
            }
        }
    }
    LaunchedEffect(Unit) {
        if (restored != null) {
            try {
                val response = api.refresh(RefreshRequest(restored.refreshToken))
                if (response.isSuccessful && response.body() != null) {
                    session = response.body()
                    secureStore.save(response.body()!!)
                    section = if (response.body()?.role == "ADMIN") "Tổng quan" else "Chấm công"
                    loadDashboard()
                } else if (response.code() == 401) {
                    secureStore.clear(); session = null; page = "login"
                }
            } catch (_: Exception) { message = "Không kết nối được Backend để khôi phục phiên." }
        }
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) step = "biometric" else { step = "idle"; message = "Cần cấp quyền vị trí để chấm công." }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) step = "scan" else { step = "idle"; message = "Cần cấp quyền camera để quét QR." }
    }
    LaunchedEffect(step) {
        when (step) {
            "locationPermission" -> if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) step = "biometric" else locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            "biometric" -> activity.authenticateAttendance { ok, error -> if (ok) step = "location" else { step = "idle"; message = error } }
            "location" -> {
                val location = activity.currentLocation()
                if (location == null || location.accuracy > 100f || System.currentTimeMillis() - location.time > 300_000) {
                    step = "idle"; message = "GPS chưa sẵn sàng hoặc thiếu chính xác. Bật vị trí và thử lại."
                } else { pendingLocation = location; step = "cameraPermission" }
            }
            "cameraPermission" -> if (ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) step = "scan" else cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    fun submitScan(token: String) {
        val location = pendingLocation
        if (location == null) {
            step = "idle"
            message = "Không có vị trí GPS."
            return
        }
        step = "sending"
        run {
            try {
                val request = CheckRequest(token, location.latitude, location.longitude, location.accuracy.toDouble(), Instant.ofEpochMilli(location.time).toString())
                val response = if (checkingIn) api.checkIn(request) else api.checkOut(request)
                if (response.isSuccessful) {
                    val tr = api.today(); val sr = api.summary(); val hr = api.history()
                    if (tr.isSuccessful && sr.isSuccessful && hr.isSuccessful) { today = tr.body(); summary = sr.body(); history = hr.body().orEmpty() }
                    message = if (checkingIn) "Check-in thành công." else "Check-out thành công."
                } else message = when (response.code()) {
                    401 -> "Phiên đăng nhập hết hạn. Hãy đăng nhập lại."
                    403 -> "Tài khoản chưa được Admin gắn với nhân viên đang hoạt động."
                    409 -> "Ca này đã được chấm công hoặc QR vừa được sử dụng."
                    else -> runCatching { JsonParser.parseString(response.errorBody()?.string()).asJsonObject.get("error").asString }.getOrNull() ?: "Chấm công thất bại (${response.code()}). Kiểm tra QR, GPS và ca làm việc."
                }
            } finally { step = "idle"; pendingLocation = null }
        }
    }
    val qrImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scanQrImage(activity, uri) { token ->
            if (token != null) submitScan(token) else message = "Ảnh không chứa mã QR hợp lệ."
        }
    }
    fun logout() {
        val old = session
        scope.launch {
            try { if (old != null) api.logout(RefreshRequest(old.refreshToken)) } catch (_: Exception) { }
            secureStore.clear(); session = null; page = "login"; section = "Tổng quan"; password = ""; issuedQr = null; today = null; history = emptyList(); message = "Đã đăng xuất."
        }
    }
    Scaffold(topBar = {
        Surface(color = blue) {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("WorkTrack", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp))
                if (session != null && page == "dashboard") TextButton(onClick = ::logout) { Text("Đăng xuất", color = Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                "login" -> {
                    Text("Đăng nhập", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { run { val r = api.login(LoginRequest(username, password)); if (r.isSuccessful) { val demo = api.demoOtp(username, "LOGIN").body()?.get("code")?.asString; if (demo != null) { val verified = api.otp(OtpRequest(username, demo)); if (verified.isSuccessful && verified.body() != null) { session = verified.body(); secureStore.save(verified.body()!!); section = if (verified.body()?.role == "ADMIN") "Tổng quan" else "Chấm công"; page = "dashboard"; loadDashboard() } else message = "Xác minh demo thất bại." } else { page = "loginOtp"; message = "Đã gửi OTP tới email." } } else message = "Tên đăng nhập hoặc mật khẩu không hợp lệ." } }, enabled = !loading && username.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Tiếp tục") }
                    TextButton(onClick = { otp = ""; password = ""; confirm = ""; page = "forgot" }) { Text("Quên mật khẩu?") }
                    TextButton(onClick = { page = "register" }) { Text("Tạo tài khoản") }
                }
                "register" -> {
                    Text("Tạo tài khoản", style = MaterialTheme.typography.headlineMedium)
                    OutlinedTextField(fullName, { fullName = it }, label = { Text("Họ và tên") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(phone, { phone = it }, label = { Text("Điện thoại") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Xác nhận mật khẩu") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Button(onClick = { run { val r = api.register(RegisterRequest(fullName, username, email, phone.ifBlank { null }, password, confirm)); if (r.isSuccessful) { val demo = api.demoOtp(username, "REGISTER").body()?.get("code")?.asString; if (demo != null) { val verified = api.verifyRegistration(OtpRequest(username, demo)); if (verified.isSuccessful) { page = "login"; message = "Đã tạo tài khoản. Hãy đăng nhập." } else message = "Xác minh demo thất bại." } else { page = "registerOtp"; message = "Đã gửi OTP tới email." } } else message = "Đăng ký thất bại. Kiểm tra dữ liệu hoặc tài khoản đã tồn tại." } }, enabled = !loading && fullName.isNotBlank() && email.isNotBlank() && password == confirm, modifier = Modifier.fillMaxWidth()) { Text("Đăng ký") }
                    TextButton(onClick = { page = "login" }) { Text("Quay lại") }
                }
                "loginOtp", "registerOtp" -> {
                    Text("Xác minh OTP", style = MaterialTheme.typography.headlineMedium)
                    Text("Mã có hiệu lực 5 phút và tối đa 5 lần thử.")
                    OutlinedTextField(otp, { otp = it }, label = { Text("Mã 6 chữ số") }, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { run {
                        if (page == "registerOtp") { val r = api.verifyRegistration(OtpRequest(username, otp)); if (r.isSuccessful) { page = "login"; message = "Kích hoạt thành công. Hãy đăng nhập." } else message = "OTP không hợp lệ." }
                        else { val r = api.otp(OtpRequest(username, otp)); if (r.isSuccessful && r.body() != null) { session = r.body(); secureStore.save(r.body()!!); section = if (r.body()?.role == "ADMIN") "Tổng quan" else "Chấm công"; page = "dashboard"; loadDashboard() } else message = "OTP không hợp lệ." }
                    } }, enabled = !loading && otp.length == 6, modifier = Modifier.fillMaxWidth()) { Text("Xác minh") }
                    TextButton(onClick = { page = "login" }) { Text("Quay lại") }
                }
                "forgot" -> {
                    Text("Quên mật khẩu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("Nhập username. Mã OTP đặt lại mật khẩu sẽ được gửi tới email đã đăng ký.")
                    OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { run {
                        val r = api.forgotPassword(ForgotPasswordRequest(username.trim()))
                        if (r.isSuccessful) {
                            otp = api.demoOtp(username, "RESET").body()?.get("code")?.asString.orEmpty()
                            page = "reset"; message = "Đã gửi OTP đặt lại mật khẩu."
                        } else message = "Không thể gửi OTP lúc này."
                    } }, enabled = !loading && username.trim().length >= 3, modifier = Modifier.fillMaxWidth()) { Text("Gửi OTP") }
                    TextButton(onClick = { page = "login" }) { Text("Quay lại đăng nhập") }
                }
                "reset" -> {
                    Text("Đặt lại mật khẩu", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    OutlinedTextField(otp, { otp = it.filter(Char::isDigit).take(6) }, label = { Text("Mã OTP 6 chữ số") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(password, { password = it }, label = { Text("Mật khẩu mới") }, supportingText = { Text("Ít nhất 8 ký tự, có chữ hoa, chữ thường và số") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(confirm, { confirm = it }, label = { Text("Nhập lại mật khẩu mới") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = { run {
                        val r = api.resetPassword(ResetPasswordRequest(username.trim(), otp, password))
                        if (r.isSuccessful) { page = "login"; otp = ""; password = ""; confirm = ""; message = "Đặt lại mật khẩu thành công. Hãy đăng nhập." }
                        else message = "OTP không hợp lệ, đã hết hạn hoặc mật khẩu chưa đủ mạnh."
                    } }, enabled = !loading && otp.length == 6 && password.length >= 8 && password == confirm, modifier = Modifier.fillMaxWidth()) { Text("Đặt lại mật khẩu") }
                    TextButton(onClick = { page = "forgot" }) { Text("Gửi lại OTP") }
                }
                "dashboard" -> {
                    Text("Xin chào, ${session?.fullName}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    when (session?.role) {
                        "ADMIN" -> {
                            listOf("Tổng quan", "Nhân viên", "Phòng ban", "Ca làm", "Phân ca", "Văn phòng", "Duyệt đơn", "Báo cáo", "Người dùng", "Tài khoản").chunked(3).forEach { labels ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    labels.forEach { label -> OutlinedButton(onClick = { section = label }, enabled = section != label, modifier = Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text(label, style = MaterialTheme.typography.labelSmall) } }
                                }
                            }
                            if (section == "Tài khoản") {
                                AccountScreen(api, onProfileUpdated = { updated -> session = session?.copy(fullName = updated.fullName); session?.let(secureStore::save) }) { message = it }
                            } else if (section == "Người dùng") {
                                UserManagement(api) { message = it }
                            } else if (section != "Tổng quan") {
                                AdminManagement(api, section) { message = it }
                            } else {
                            Metric("Tổng nhân viên", (adminSummary?.totalEmployees ?: employees.count { it.active }).toString())
                            Metric("Đang làm hôm nay", (adminSummary?.workingToday ?: 0).toString())
                            Metric("Đi trễ hôm nay", (adminSummary?.lateToday ?: 0).toString())
                            Metric("Nghỉ hôm nay", (adminSummary?.onLeaveToday ?: 0).toString())
                            Metric("Yêu cầu chờ duyệt", (adminSummary?.pendingRequests ?: 0).toString())
                            Metric("Đã check-in hôm nay", (adminSummary?.checkedInToday ?: 0).toString())
                            if (!adminSummary?.recent.isNullOrEmpty()) {
                                Text("Hoạt động gần đây", style = MaterialTheme.typography.titleLarge)
                                adminSummary?.recent?.forEach { ItemCard(it.action.replace('_', ' '), "Mã ${it.subject} · ${formatUtc(it.atUtc)}") }
                            }
                            Text("Danh sách nhân viên", style = MaterialTheme.typography.titleLarge)
                            if (employees.isEmpty()) Text("Chưa có nhân viên hoặc đang tải dữ liệu.")
                            employees.forEach { ItemCard("${it.employeeCode} · ${it.fullName}", "${it.position} · ${it.email}") }
                            Text("Phòng ban", style = MaterialTheme.typography.titleLarge)
                            departments.forEach { ItemCard(it.name, it.code) }
                            Text("Mã QR chấm công", style = MaterialTheme.typography.titleLarge)
                            Text("Hiển thị mã trên màn hình này để nhân viên quét bằng camera. Mã hết hạn sau 5 phút và dùng một lần.")
                            offices.forEach { office ->
                                Button(onClick = { run { val response = api.generateQr(office.id); if (response.isSuccessful) issuedQr = response.body() else message = "Không tạo được mã QR." } }, enabled = !loading) { Text("Tạo QR · ${office.name}") }
                            }
                            issuedQr?.let { qr ->
                                QrImage(qr.token)
                                Text("Hết hạn: ${qr.expiresUtc.take(19).replace('T',' ')} UTC")
                                OutlinedButton(onClick = { message = if (saveQrImage(activity, qr.token)) "Đã lưu ảnh QR vào Pictures/WorkTrack." else "Không lưu được ảnh QR." }) { Text("Lưu ảnh QR để demo trên một Emulator") }
                            }
                            }
                        }
                        "EMPLOYEE" -> {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Chấm công", "Đơn từ", "Thông báo", "Tài khoản").forEach { label -> OutlinedButton(onClick = { section = label }, enabled = section != label, modifier = Modifier.weight(1f), contentPadding = PaddingValues(2.dp)) { Text(label, style = MaterialTheme.typography.labelSmall) } }
                            }
                            if (section == "Đơn từ") {
                                EmployeeRequests(api) { message = it }
                            } else if (section == "Thông báo") {
                                EmployeeNotifications(api) { message = it }
                            } else if (section == "Tài khoản") {
                                AccountScreen(api, onProfileUpdated = { updated -> session = session?.copy(fullName = updated.fullName); session?.let(secureStore::save) }) { message = it }
                            } else {
                            Metric("Trạng thái hôm nay", if (today?.record == null) "Chưa check-in" else if (today?.record?.checkOutUtc == null) "Đang làm việc" else "Đã hoàn thành")
                            Text(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy · HH:mm", Locale.forLanguageTag("vi-VN")).format(ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))))
                            val assignedShift = shiftList.firstOrNull { it.id == runCatching { today?.assignment?.get("shiftId")?.asInt }.getOrNull() }
                            if (assignedShift != null) ItemCard("Ca hôm nay: ${assignedShift.name}", "${assignedShift.startTime.take(5)} - ${assignedShift.endTime.take(5)} · cho phép trễ ${assignedShift.gracePeriodMinutes} phút")
                            today?.record?.let { ItemCard("Giờ vào: ${formatUtc(it.checkInUtc)}", "Giờ ra: ${it.checkOutUtc?.let(::formatUtc) ?: "Chưa check-out"}") }
                            Metric("Số ngày làm", "${summary?.days ?: 0}")
                            Metric("Đi trễ", "${summary?.late ?: 0}")
                            Metric("Tổng giờ làm", "${"%.1f".format(summary?.hours ?: 0.0)}")
                            SmartAssistant(today, summary, history, shiftList) { section = "Đơn từ" }
                            Text("Chấm công", style = MaterialTheme.typography.titleLarge)
                            Text("Bấm Check-in để xác thực vân tay, kiểm tra GPS và quét QR bằng camera. Không cần nhập token.")
                            Button(onClick = { checkingIn = true; step = "locationPermission" }, enabled = !loading && step == "idle" && today?.record == null) { Text("Check-in") }
                            Button(onClick = { checkingIn = false; step = "locationPermission" }, enabled = !loading && step == "idle" && today?.record != null && today?.record?.checkOutUtc == null) { Text("Check-out") }
                            if (step == "scan") {
                                Text("Đưa camera vào mã QR do Admin đang hiển thị.")
                                QrScanner(onScanned = { submitScan(it) }, onError = { message = it })
                                OutlinedButton(onClick = { qrImagePicker.launch("image/*") }) { Text("Chọn ảnh QR từ máy (demo)") }
                                OutlinedButton(onClick = { step = "idle" }) { Text("Hủy quét") }
                            }
                            if (step == "location" || step == "sending") LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("Lịch sử", style = MaterialTheme.typography.titleLarge)
                            if (history.isEmpty()) Text("Chưa có bản ghi chấm công.")
                            history.forEach { ItemCard(formatUtc(it.checkInUtc), if (it.checkOutUtc == null) "Chưa check-out" else "Ra: ${formatUtc(it.checkOutUtc)}") }
                            }
                        }
                        "CUSTOMER" -> {
                            Text("Cổng thông tin WorkTrack", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            ItemCard("Thông tin công ty", "WorkTrack · Nền tảng quản lý nhân sự và chấm công an toàn")
                            Text("Thông báo công khai", style = MaterialTheme.typography.titleLarge)
                            ItemCard("Chào mừng đến WorkTrack", "Tài khoản Customer có thể cập nhật hồ sơ và bảo mật tại đây.")
                            Text("Tài khoản khách hàng không có quyền truy cập chấm công.")
                            AccountScreen(api, onProfileUpdated = { updated -> session = session?.copy(fullName = updated.fullName); session?.let(secureStore::save) }) { message = it }
                        }
                    }
                    Button(onClick = { loadDashboard() }, enabled = !loading) { Text("Làm mới") }
                    OutlinedButton(onClick = ::logout) { Text("Đăng xuất") }
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message.isNotBlank()) Text(message, color = if (message.contains("thành công") || message.contains("Đã gửi")) Color(0xFF059669) else Color(0xFFB91C1C))
        }
    }
}

@Composable private fun Metric(label: String, value: String) { ItemCard(label, value) }
@Composable private fun ItemCard(title: String, detail: String) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) { Text(title, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)); Text(detail) } }
}
