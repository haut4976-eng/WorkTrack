package com.worktrack.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun AccountScreen(api: WorkTrackApi, onProfileUpdated: (Profile) -> Unit, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            try {
                val response = api.me()
                if (response.isSuccessful && response.body() != null) {
                    profile = response.body()
                    fullName = response.body()!!.fullName
                    email = response.body()!!.email
                    phone = response.body()!!.phone.orEmpty()
                } else onMessage("Không tải được hồ sơ.")
            } catch (_: Exception) { onMessage("Không kết nối được Backend.") }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    Text("Hồ sơ và bảo mật", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    profile?.let { Text("${it.username} · ${it.role}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    OutlinedTextField(fullName, { fullName = it }, label = { Text("Họ và tên") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(phone, { phone = it }, label = { Text("Điện thoại") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            scope.launch {
                loading = true
                try {
                    val response = api.updateProfile(ProfileRequest(fullName.trim(), email.trim(), phone.trim().ifBlank { null }))
                    if (response.isSuccessful && response.body() != null) {
                        profile = response.body(); onProfileUpdated(response.body()!!); onMessage("Cập nhật hồ sơ thành công.")
                    } else onMessage("Hồ sơ không hợp lệ hoặc email đã được sử dụng.")
                } catch (_: Exception) { onMessage("Không kết nối được Backend.") }
                finally { loading = false }
            }
        },
        enabled = !loading && fullName.trim().length >= 2 && email.contains("@"),
        modifier = Modifier.fillMaxWidth()
    ) { Text("Lưu hồ sơ") }

    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    Text("Đổi mật khẩu", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    OutlinedTextField(currentPassword, { currentPassword = it }, label = { Text("Mật khẩu hiện tại") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(newPassword, { newPassword = it }, label = { Text("Mật khẩu mới") }, supportingText = { Text("Ít nhất 8 ký tự, có chữ hoa, chữ thường và số") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(confirmPassword, { confirmPassword = it }, label = { Text("Nhập lại mật khẩu mới") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
    Button(
        onClick = {
            scope.launch {
                loading = true
                try {
                    val response = api.changePassword(ChangePasswordRequest(currentPassword, newPassword))
                    if (response.isSuccessful) {
                        currentPassword = ""; newPassword = ""; confirmPassword = ""; onMessage("Đổi mật khẩu thành công.")
                    } else onMessage("Mật khẩu hiện tại không đúng hoặc mật khẩu mới chưa đủ mạnh.")
                } catch (_: Exception) { onMessage("Không kết nối được Backend.") }
                finally { loading = false }
            }
        },
        enabled = !loading && currentPassword.isNotBlank() && newPassword.length >= 8 && newPassword == confirmPassword,
        modifier = Modifier.fillMaxWidth()
    ) { Text("Đổi mật khẩu") }
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
}
