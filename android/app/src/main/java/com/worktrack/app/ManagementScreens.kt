package com.worktrack.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
private fun Field(value: String, change: (String) -> Unit, label: String) {
    OutlinedTextField(value, change, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}

@Composable
private fun RowCard(title: String, detail: String, select: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = select)) {
        Column(Modifier.padding(14.dp)) { Text(title, fontWeight = FontWeight.Bold); Text(detail) }
    }
}

@Composable
fun AdminManagement(api: WorkTrackApi, section: String, show: (String) -> Unit) {
    var departments by remember { mutableStateOf<List<Department>>(emptyList()) }
    var employees by remember { mutableStateOf<List<Employee>>(emptyList()) }
    var shifts by remember { mutableStateOf<List<Shift>>(emptyList()) }
    var assignments by remember { mutableStateOf<List<Assignment>>(emptyList()) }
    var offices by remember { mutableStateOf<List<Office>>(emptyList()) }
    var leaves by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var corrections by remember { mutableStateOf<List<CorrectionRequest>>(emptyList()) }
    var reports by remember { mutableStateOf<List<Attendance>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    fun act(call: suspend () -> Boolean) {
        scope.launch {
            busy = true
            try { show(if (call()) "Đã lưu thành công." else "Thao tác không thành công. Kiểm tra dữ liệu và thử lại."); refresh++ }
            catch (e: Exception) { show("Lỗi kết nối: ${e.localizedMessage}") }
            finally { busy = false }
        }
    }
    LaunchedEffect(section, refresh) {
        try {
            when (section) {
                "Nhân viên" -> { employees = api.employees().body().orEmpty(); departments = api.departments().body().orEmpty() }
                "Phòng ban" -> departments = api.departments().body().orEmpty()
                "Ca làm" -> shifts = api.shifts().body().orEmpty()
                "Phân ca" -> { assignments = api.assignments().body().orEmpty(); employees = api.employees().body().orEmpty(); shifts = api.shifts().body().orEmpty() }
                "Văn phòng" -> offices = api.offices().body().orEmpty()
                "Duyệt đơn" -> { leaves = api.leaves().body().orEmpty(); corrections = api.corrections().body().orEmpty() }
                "Báo cáo" -> { reports = api.reports().body().orEmpty(); employees = api.employees().body().orEmpty() }
            }
        } catch (e: Exception) { show("Không tải được dữ liệu: ${e.localizedMessage}") }
    }
    Text(section, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    when (section) {
        "Phòng ban" -> {
            var id by remember { mutableIntStateOf(0) }; var code by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var description by remember { mutableStateOf("") }
            departments.forEach { d -> RowCard(d.name, d.code) { id=d.id;code=d.code;name=d.name;description=d.description } }
            Text(if (id==0) "Thêm phòng ban" else "Sửa phòng ban #$id")
            Field(code,{code=it},"Mã phòng ban");Field(name,{name=it},"Tên phòng ban");Field(description,{description=it},"Mô tả")
            Button(onClick={act { val req=DepartmentRequest(code,name,description); if(id==0) api.createDepartment(req).isSuccessful else api.updateDepartment(id,req).isSuccessful }},enabled=!busy&&code.isNotBlank()&&name.isNotBlank()){Text("Lưu phòng ban")}
            if(id!=0) { OutlinedButton(onClick={id=0;code="";name="";description=""}){Text("Tạo mới")}; TextButton(onClick={act { api.deleteDepartment(id).isSuccessful };id=0}){Text("Ngừng sử dụng phòng ban")} }
        }
        "Nhân viên" -> {
            var id by remember { mutableIntStateOf(0) }; var code by remember { mutableStateOf("") }; var name by remember { mutableStateOf("") }; var email by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var position by remember { mutableStateOf("") }; var hireDate by remember { mutableStateOf(LocalDate.now().toString()) }; var departmentId by remember { mutableIntStateOf(0) }
            var search by remember { mutableStateOf("") }; var filterDepartment by remember { mutableIntStateOf(0) }; var status by remember { mutableStateOf("ACTIVE") }
            Text("Tài khoản nhân viên cần đăng ký và xác minh OTP trước. Chọn phòng ban rồi điền email đã đăng ký.")
            Field(search, { search=it }, "Tìm theo mã, họ tên hoặc email")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ACTIVE" to "Đang làm", "INACTIVE" to "Đã nghỉ", "ALL" to "Tất cả").forEach { (value,label) -> FilterChip(selected=status==value,onClick={status=value},label={Text(label)}) }
            }
            Text("Lọc phòng ban: ${departments.firstOrNull{it.id==filterDepartment}?.name ?: "Tất cả"}")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected=filterDepartment==0,onClick={filterDepartment=0},label={Text("Tất cả")})
                departments.forEach { d -> FilterChip(selected=filterDepartment==d.id,onClick={filterDepartment=d.id},label={Text(d.name)}) }
            }
            val filteredEmployees=employees.filter { e ->
                (search.isBlank()||e.employeeCode.contains(search,true)||e.fullName.contains(search,true)||e.email.contains(search,true)) &&
                    (filterDepartment==0||e.departmentId==filterDepartment) && (status=="ALL"||e.active==(status=="ACTIVE"))
            }
            if(filteredEmployees.isEmpty()) Text("Không có nhân viên phù hợp.")
            filteredEmployees.forEach { e -> RowCard("${e.employeeCode} · ${e.fullName}","${e.email} · ${e.position} · ${if(e.active)"Đang làm" else "Đã nghỉ"}") { id=e.id;code=e.employeeCode;name=e.fullName;email=e.email;phone=e.phone.orEmpty();position=e.position;hireDate=e.hireDate;departmentId=e.departmentId } }
            Text(if(id==0) "Thêm nhân viên" else "Sửa nhân viên #$id")
            Field(code,{code=it},"Mã nhân viên");Field(name,{name=it},"Họ tên");Field(email,{email=it},"Email tài khoản");Field(phone,{phone=it},"Điện thoại");Field(position,{position=it},"Chức danh");Field(hireDate,{hireDate=it},"Ngày vào làm YYYY-MM-DD")
            Text("Phòng ban đã chọn: ${departments.firstOrNull{it.id==departmentId}?.name ?: "Chưa chọn"}")
            departments.forEach { d -> OutlinedButton(onClick={departmentId=d.id}){Text(d.name)} }
            Button(onClick={act { val req=EmployeeRequest(code,name,email,phone.ifBlank{null},departmentId,position,hireDate);if(id==0)api.createEmployee(req).isSuccessful else api.updateEmployee(id,req).isSuccessful }},enabled=!busy&&code.isNotBlank()&&name.isNotBlank()&&email.isNotBlank()&&departmentId!=0){Text("Lưu nhân viên")}
            if(id!=0) { OutlinedButton(onClick={id=0;code="";name="";email="";phone="";position="";departmentId=0}){Text("Tạo mới")};TextButton(onClick={act { api.deleteEmployee(id).isSuccessful };id=0}){Text("Ngừng sử dụng nhân viên")} }
        }
        "Ca làm" -> {
            var id by remember { mutableIntStateOf(0) }; var name by remember { mutableStateOf("") }; var start by remember { mutableStateOf("08:00:00") }; var end by remember { mutableStateOf("17:00:00") }; var grace by remember { mutableStateOf("15") }; var description by remember { mutableStateOf("") }
            shifts.forEach { s -> RowCard(s.name,"${s.startTime} – ${s.endTime}"){id=s.id;name=s.name;start=s.startTime;end=s.endTime;grace=s.gracePeriodMinutes.toString();description=s.description} }
            Text(if(id==0) "Thêm ca" else "Sửa ca #$id")
            Field(name,{name=it},"Tên ca");Field(start,{start=it},"Bắt đầu HH:mm:ss");Field(end,{end=it},"Kết thúc HH:mm:ss");Field(grace,{grace=it},"Phút cho phép đi trễ");Field(description,{description=it},"Mô tả")
            Button(onClick={act { val req=ShiftRequest(name,start,end,grace.toIntOrNull()?:0,description);if(id==0)api.createShift(req).isSuccessful else api.updateShift(id,req).isSuccessful }},enabled=!busy&&name.isNotBlank()&&grace.toIntOrNull()!=null){Text("Lưu ca")}
            if(id!=0) { OutlinedButton(onClick={id=0;name=""}){Text("Tạo mới")};TextButton(onClick={act { api.deleteShift(id).isSuccessful };id=0}){Text("Ngừng sử dụng ca")} }
        }
        "Phân ca" -> {
            assignments.forEach { a -> RowCard("${a.workDate} · ${employees.firstOrNull{it.id==a.employeeId}?.fullName ?: a.employeeId}",shifts.firstOrNull{it.id==a.shiftId}?.name.orEmpty()){act { api.deleteAssignment(a.id).isSuccessful }} }
            Text("Chạm phân ca ở trên để xóa nếu chưa có bản ghi chấm công.")
            var employeeId by remember { mutableIntStateOf(0) }; var shiftId by remember { mutableIntStateOf(0) }; var day by remember { mutableStateOf(LocalDate.now().toString()) }
            Text("Nhân viên: ${employees.firstOrNull{it.id==employeeId}?.fullName ?: "Chưa chọn"}")
            employees.forEach { e -> OutlinedButton(onClick={employeeId=e.id}){Text(e.fullName)} }
            Text("Ca: ${shifts.firstOrNull{it.id==shiftId}?.name ?: "Chưa chọn"}")
            shifts.forEach { s -> OutlinedButton(onClick={shiftId=s.id}){Text(s.name)} }
            Field(day,{day=it},"Ngày YYYY-MM-DD")
            Button(onClick={act { api.createAssignment(AssignmentRequest(employeeId,shiftId,day)).isSuccessful }},enabled=!busy&&employeeId!=0&&shiftId!=0){Text("Lưu phân ca")}
        }
        "Văn phòng" -> {
            var id by remember { mutableIntStateOf(0) };var name by remember { mutableStateOf("") };var lat by remember { mutableStateOf("") };var lon by remember { mutableStateOf("") };var radius by remember { mutableStateOf("250") }
            offices.forEach { o -> RowCard(o.name,"${o.latitude}, ${o.longitude} · ${o.allowedRadiusMeters} m"){id=o.id;name=o.name;lat=o.latitude.toString();lon=o.longitude.toString();radius=o.allowedRadiusMeters.toString()} }
            Text(if(id==0) "Thêm văn phòng" else "Sửa văn phòng #$id")
            Field(name,{name=it},"Tên văn phòng");Field(lat,{lat=it},"Vĩ độ");Field(lon,{lon=it},"Kinh độ");Field(radius,{radius=it},"Bán kính cho phép (m)")
            Button(onClick={act { val req=Office(id,name,lat.toDouble(),lon.toDouble(),radius.toDouble());if(id==0)api.createOffice(req).isSuccessful else api.updateOffice(id,req).isSuccessful }},enabled=!busy&&name.isNotBlank()&&lat.toDoubleOrNull()!=null&&lon.toDoubleOrNull()!=null&&radius.toDoubleOrNull()!=null){Text("Lưu văn phòng")}
            if(id!=0)OutlinedButton(onClick={id=0;name="";lat="";lon=""}){Text("Tạo mới")}
        }
        "Duyệt đơn" -> {
            var reason by remember { mutableStateOf("") }
            Field(reason,{reason=it},"Lý do từ chối")
            Text("Đơn xin nghỉ",style=MaterialTheme.typography.titleMedium)
            leaves.forEach { r -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Nhân viên #${r.employeeId} · ${r.startDate} – ${r.endDate}");Text("${r.reason} · ${r.status}");if(r.status=="PENDING")Row { Button(onClick={act {api.approveLeave(r.id).isSuccessful}},enabled=!busy){Text("Duyệt")};TextButton(onClick={act {api.rejectLeave(r.id,ReviewInput(reason)).isSuccessful}},enabled=!busy&&reason.isNotBlank()){Text("Từ chối")} } } } }
            Text("Yêu cầu chỉnh công",style=MaterialTheme.typography.titleMedium)
            corrections.forEach { r -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { Text("Nhân viên #${r.employeeId} · Bản ghi #${r.attendanceRecordId}");Text("${r.proposedCheckOutUtc} · ${r.reason} · ${r.status}");if(r.status=="PENDING")Row { Button(onClick={act {api.approveCorrection(r.id).isSuccessful}},enabled=!busy){Text("Duyệt")};TextButton(onClick={act {api.rejectCorrection(r.id,ReviewInput(reason)).isSuccessful}},enabled=!busy&&reason.isNotBlank()){Text("Từ chối")} } } } }
        }
        "Báo cáo" -> {
            Text("${reports.size} bản ghi gần nhất · ${reports.count{it.late}} đi trễ · ${reports.count{it.earlyLeave}} về sớm")
            reports.forEach { r -> RowCard("${employees.firstOrNull{it.id==r.employeeId}?.fullName ?: "Nhân viên #${r.employeeId}"} · ${formatUtc(r.checkInUtc)}", "Giờ ra: ${r.checkOutUtc?.let(::formatUtc) ?: "Chưa có"} · ${if(r.late)"Đi trễ" else "Đúng giờ"}") {} }
        }
    }
}

@Composable
fun EmployeeRequests(api: WorkTrackApi, show: (String) -> Unit) {
    var leaves by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var corrections by remember { mutableStateOf<List<CorrectionRequest>>(emptyList()) }
    var history by remember { mutableStateOf<List<Attendance>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope=rememberCoroutineScope()
    fun act(call:suspend()->Boolean){scope.launch{busy=true;try{show(if(call())"Đã gửi yêu cầu." else "Yêu cầu không hợp lệ.");refresh++}catch(e:Exception){show("Lỗi kết nối: ${e.localizedMessage}")}finally{busy=false}}}
    LaunchedEffect(refresh){try{leaves=api.leaves().body().orEmpty();corrections=api.corrections().body().orEmpty();history=api.history().body().orEmpty()}catch(e:Exception){show("Không tải được đơn: ${e.localizedMessage}")}}
    var start by remember { mutableStateOf(LocalDate.now().toString()) };var end by remember { mutableStateOf(LocalDate.now().toString()) };var reason by remember { mutableStateOf("") }; var editingId by remember { mutableIntStateOf(0) }
    Text("Xin nghỉ",style=MaterialTheme.typography.titleLarge)
    Field(start,{start=it},"Từ ngày YYYY-MM-DD");Field(end,{end=it},"Đến ngày YYYY-MM-DD");Field(reason,{reason=it},"Lý do")
    Button(onClick={act{val req=LeaveInput(start,end,reason);val ok=if(editingId==0)api.createLeave(req).isSuccessful else api.updateLeave(editingId,req).isSuccessful;if(ok){editingId=0;reason=""};ok}},enabled=!busy&&reason.isNotBlank()){Text(if(editingId==0)"Gửi đơn nghỉ" else "Lưu đơn nghỉ")}
    if(editingId!=0)TextButton(onClick={editingId=0;reason=""}){Text("Tạo đơn mới")}
    leaves.forEach{r->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text("${r.startDate} – ${r.endDate} · ${r.status}");Text(r.reason);if(r.status=="PENDING")Row { TextButton(onClick={editingId=r.id;start=r.startDate;end=r.endDate;reason=r.reason}){Text("Sửa")};TextButton(onClick={act{api.deleteLeave(r.id).isSuccessful}}){Text("Hủy đơn")} }}}}
    Text("Bổ sung giờ ra khi quên check-out",style=MaterialTheme.typography.titleLarge)
    var recordId by remember { mutableIntStateOf(0) };var proposed by remember { mutableStateOf("") };var correctionReason by remember { mutableStateOf("") }
    Text("Chọn lần chấm công chưa có giờ ra:")
    history.filter{it.checkOutUtc==null}.forEach{r->RowCard("Giờ vào ${formatUtc(r.checkInUtc)}",if(recordId==r.id)"Đã chọn" else "Chạm để chọn"){recordId=r.id}}
    Field(proposed,{proposed=it},"Giờ ra (Việt Nam) YYYY-MM-DD HH:mm");Field(correctionReason,{correctionReason=it},"Lý do")
    Button(onClick={act{val utc=LocalDateTime.parse(proposed.trim().replace(' ','T')).atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant().toString();api.createCorrection(CorrectionInput(recordId,utc,correctionReason)).isSuccessful}},enabled=!busy&&recordId!=0&&runCatching{LocalDateTime.parse(proposed.trim().replace(' ','T'))}.isSuccess&&correctionReason.isNotBlank()){Text("Gửi yêu cầu chỉnh công")}
    corrections.forEach{r->RowCard("Bản ghi #${r.attendanceRecordId} · ${r.status}","${r.proposedCheckOutUtc} · ${r.reason}"){} }
}

@Composable
fun EmployeeNotifications(api: WorkTrackApi, show: (String) -> Unit) {
    var leaves by remember { mutableStateOf<List<LeaveRequest>>(emptyList()) }
    var corrections by remember { mutableStateOf<List<CorrectionRequest>>(emptyList()) }
    LaunchedEffect(Unit) {
        try { leaves=api.leaves().body().orEmpty();corrections=api.corrections().body().orEmpty() }
        catch(e:Exception){show("Không tải được thông báo: ${e.localizedMessage}")}
    }
    Text("Thông báo",style=MaterialTheme.typography.titleLarge)
    val reviewedLeaves=leaves.filter{it.status!="PENDING"}
    val reviewedCorrections=corrections.filter{it.status!="PENDING"}
    if(reviewedLeaves.isEmpty()&&reviewedCorrections.isEmpty())Text("Chưa có quyết định mới về đơn từ.")
    reviewedLeaves.forEach{r->RowCard("Đơn nghỉ ${r.status}","${r.startDate} – ${r.endDate} · ${r.reason}"){} }
    reviewedCorrections.forEach{r->RowCard("Chỉnh công ${r.status}","${r.proposedCheckOutUtc} · ${r.reason}"){} }
}
