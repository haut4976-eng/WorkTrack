# Kiểm thử ngày 22/10/2026

## Đã chạy

- [x] `dotnet build` và `android/gradlew.bat assembleDebug` thành công.
- [x] `START_DEMO.cmd`/`start-demo.ps1` khởi động Backend, build APK, cài và mở app trên Pixel_7 Emulator.
- [x] API smoke với SQLite riêng: OTP, quên/đặt lại/đổi mật khẩu, hồ sơ, quản lý user, quyền 401/403, CRUD, dashboard, GPS ngoài vùng, QR một lần, Check-in/out và refresh rotation.
- [x] Admin tạo QR trong app và lưu ảnh; Employee đăng nhập demo, xác thực fingerprint, lấy GPS, chọn ảnh QR, Check-in thành công.
- [x] Admin tạo QR mới; Employee xác thực fingerprint/GPS và Check-out thành công; lịch sử hiện giờ ra.
- [x] Khi Emulator chưa đăng ký vân tay, app báo lỗi rõ và không thoát. Khi đã đăng ký, BiometricPrompt mở và xác thực thành công.
- [x] Đóng hẳn và mở lại app: phiên được khôi phục, `/auth/refresh` trả 200 và dashboard tải lại.
- [x] Admin mở màn Hồ sơ và bảo mật; Employee hiển thị Smart Attendance Assistant trên Pixel_10_Pro Emulator, không crash.
- [x] Customer đăng nhập qua OTP demo và hiển thị dashboard riêng, thông tin công ty, thông báo công khai, hồ sơ bảo mật.

## Chưa xác nhận đầy đủ

- [ ] CameraX/ML Kit quét trực tiếp QR từ camera trên thiết bị thật; mới thử preview và giải mã ảnh QR qua ML Kit.
- [ ] Toàn bộ biểu mẫu quản trị, đăng ký Customer, xin nghỉ/chỉnh công qua giao diện Emulator; API đã được test một phần.
- [ ] OTP sai/hết hạn trên giao diện, SMTP thật và release HTTPS; reset/đổi mật khẩu đã PASS ở API.
- [ ] UI trên nhiều kích cỡ thiết bị, ca qua đêm và tranh chấp check-in đồng thời.
- [ ] Push notification; hiện chỉ có thông báo trạng thái đơn khi mở app.
