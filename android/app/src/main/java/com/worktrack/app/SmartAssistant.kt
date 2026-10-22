package com.worktrack.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val vnZone = ZoneId.of("Asia/Ho_Chi_Minh")
private fun localDate(utc: String) = runCatching {
    val instant = runCatching { Instant.parse(utc) }.getOrElse { LocalDateTime.parse(utc).toInstant(ZoneOffset.UTC) }
    instant.atZone(vnZone).toLocalDate()
}.getOrNull()

@Composable
fun SmartAssistant(today: Today?, summary: Summary?, history: List<Attendance>, shifts: List<Shift>, onCorrection: () -> Unit) {
    val now = ZonedDateTime.now(vnZone)
    val assignedShiftId = runCatching { today?.assignment?.get("shiftId")?.asInt }.getOrNull()
    val shift = shifts.firstOrNull { it.id == assignedShiftId } ?: shifts.firstOrNull()
    val start = runCatching { LocalTime.parse(shift?.startTime) }.getOrNull()
    val end = runCatching { LocalTime.parse(shift?.endTime) }.getOrNull()
    val checkIn = today?.record?.checkInUtc ?: history.firstOrNull { it.checkOutUtc == null }?.checkInUtc
    val checkOut = today?.record?.checkOutUtc
    val minutesToStart = start?.let { java.time.Duration.between(now.toLocalTime(), it).toMinutes() }
    val endReached = if (checkIn != null && end != null) {
        val workDay = localDate(checkIn) ?: now.toLocalDate()
        val endDay = if (start != null && !end.isAfter(start)) workDay.plusDays(1) else workDay
        now.isAfter(endDay.atTime(end).atZone(vnZone).plusMinutes(30))
    } else false
    val warning = when {
        checkIn == null && minutesToStart != null && minutesToStart in 0..30 -> "Ca sắp bắt đầu trong $minutesToStart phút. Hãy chuẩn bị Check-in."
        checkIn == null && start != null && now.toLocalTime().isAfter(start.plusMinutes(shift?.gracePeriodMinutes?.toLong() ?: 0)) -> "Bạn chưa Check-in cho ca hôm nay."
        checkIn == null -> "Bạn chưa Check-in hôm nay."
        checkOut == null && endReached -> "Có thể bạn đã quên Check-out. Gửi yêu cầu bổ sung giờ ra nếu cần."
        checkOut == null -> "Bạn đang trong ca làm việc. Nhớ Check-out khi kết thúc."
        else -> "Chấm công hôm nay đã hoàn thành."
    }
    val days = summary?.days ?: 0
    val late = summary?.late ?: 0
    val percent = if (days == 0) 0 else ((days - late).coerceAtLeast(0) * 100 / days)
    val thisWeek = history.count { localDate(it.checkInUtc)?.let { date -> !date.isBefore(now.toLocalDate().minusDays(6)) && !date.isAfter(now.toLocalDate()) } == true }
    val previousWeek = history.count { localDate(it.checkInUtc)?.let { date -> !date.isBefore(now.toLocalDate().minusDays(13)) && date.isBefore(now.toLocalDate().minusDays(6)) } == true }
    val thisMonth = history.count { localDate(it.checkInUtc)?.month == now.month && localDate(it.checkInUtc)?.year == now.year }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Trợ lý chấm công", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(warning)
            if (checkIn != null && checkOut == null && endReached) TextButton(onClick = onCorrection) { Text("Gửi yêu cầu chỉnh công") }
            Text("Đúng giờ: $percent% · Về sớm: ${summary?.earlyLeave ?: 0} lần")
            Text("7 ngày gần đây: $thisWeek ngày · 7 ngày trước: $previousWeek ngày · Tháng này: $thisMonth ngày")
            Text("Tổng giờ: ${"%.1f".format(summary?.hours ?: 0.0)} giờ")
        }
    }
}
