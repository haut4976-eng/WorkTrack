# Kịch bản demo WorkTrack (10–15 phút)

1. Nhấp đúp `START_DEMO.cmd`; chờ Backend, APK và Emulator mở xong. Khi script khởi động Backend mới, OTP demo được app tự xác minh.
2. Trong Emulator đặt GPS tại `10.7769, 106.7009` và đăng ký fingerprint trong Android Settings. Sử dụng Extended controls để gửi dấu vân tay khi app yêu cầu.
3. Đăng nhập `admin / DemoPass123!`. Xem chỉ số hôm nay; mở quản trị nhân viên, người dùng, phân ca, duyệt đơn và hồ sơ. Ở Tổng quan tạo QR văn phòng rồi lưu ảnh QR vào máy.
4. Đăng xuất, đăng nhập `employee2 / DemoPass123!`. Xem ca hôm nay và Smart Attendance Assistant. Bấm Check-in → xác thực vân tay → lấy GPS → chọn ảnh QR mới nhất. Xem trạng thái và lịch sử được API cập nhật.
5. Đăng nhập Admin, tạo và lưu QR mới. Đăng nhập Employee, bấm Check-out và quét QR mới. Xem giờ ra trong lịch sử.
6. Đóng hẳn và mở lại app: Employee vẫn ở dashboard nhờ phiên lưu bằng Keystore và refresh token.
7. Minh họa bảo vệ quyền: Employee không gọi được API `/employees` (403), QR cũ không dùng lại được.
8. Ở màn đăng nhập, mở **Quên mật khẩu** để xem luồng OTP/reset; trong **Tài khoản** có sửa hồ sơ và đổi mật khẩu.

Nếu có hai thiết bị, Admin hiển thị QR trên một máy để Employee quét trực tiếp bằng camera trên máy kia. Luồng giải mã QR trực tiếp từ camera chưa được xác nhận trên thiết bị thật; demo một Emulator dùng chức năng chọn ảnh đã thử thành công.
