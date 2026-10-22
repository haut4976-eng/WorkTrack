package com.worktrack.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun UserManagement(api: WorkTrackApi, onMessage: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    var users by remember { mutableStateOf<List<AppUser>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<AppUser?>(null) }
    var role by remember { mutableStateOf("CUSTOMER") }
    var active by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var confirming by remember { mutableStateOf(false) }

    fun load() {
        scope.launch {
            loading = true
            try {
                val response = api.users()
                if (response.isSuccessful) users = response.body().orEmpty() else onMessage("Không tải được danh sách người dùng.")
            } catch (_: Exception) { onMessage("Không kết nối được Backend.") }
            finally { loading = false }
        }
    }
    LaunchedEffect(Unit) { load() }

    Text("Quản lý người dùng", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    OutlinedTextField(search, { search = it }, label = { Text("Tìm username, họ tên hoặc email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    val filtered = users.filter { search.isBlank() || it.username.contains(search, true) || it.fullName.contains(search, true) || it.email.contains(search, true) }
    if (!loading && filtered.isEmpty()) Text("Không có người dùng phù hợp.")
    filtered.forEach { user ->
        Card(Modifier.fillMaxWidth().clickable {
            selected = user; role = user.role; active = user.active
        }) {
            Column(Modifier.padding(14.dp)) {
                Text("${user.username} · ${user.fullName}", fontWeight = FontWeight.Bold)
                Text("${user.email} · ${user.role} · ${if (user.active) "Đang hoạt động" else "Đã khóa"}")
            }
        }
    }
    selected?.let { user ->
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Cập nhật ${user.username}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ADMIN", "EMPLOYEE", "CUSTOMER").forEach { value ->
                FilterChip(selected = role == value, onClick = { role = value }, label = { Text(value) })
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(if (active) "Tài khoản đang hoạt động" else "Tài khoản bị khóa")
            Switch(checked = active, onCheckedChange = { active = it })
        }
        Button(onClick = { confirming = true }, enabled = !loading, modifier = Modifier.fillMaxWidth()) { Text("Lưu quyền và trạng thái") }
    }
    if (confirming) AlertDialog(
        onDismissRequest = { confirming = false },
        title = { Text("Xác nhận cập nhật") },
        text = { Text("Phiên đăng nhập hiện tại của tài khoản này sẽ bị thu hồi.") },
        confirmButton = {
            Button(onClick = {
                confirming = false
                val user = selected ?: return@Button
                scope.launch {
                    loading = true
                    try {
                        val response = api.updateUser(user.id, UpdateUserRequest(role, active))
                        if (response.isSuccessful) { onMessage("Cập nhật người dùng thành công."); selected = null; load() }
                        else onMessage("Không cập nhật được. Tài khoản Employee cần gắn hồ sơ nhân viên và hệ thống phải còn một Admin.")
                    } catch (_: Exception) { onMessage("Không kết nối được Backend.") }
                    finally { loading = false }
                }
            }) { Text("Xác nhận") }
        },
        dismissButton = { TextButton(onClick = { confirming = false }) { Text("Hủy") } }
    )
    if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
}
