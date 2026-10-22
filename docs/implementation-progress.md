# Tiến độ triển khai

Kiểm tra ngày 22/10/2026. `DONE` nghĩa là đã build và thử luồng nêu ở cột bằng chứng; `UNVERIFIED` nghĩa là đã viết/build nhưng chưa thử đủ luồng; `MISSING` nghĩa là chưa có.

| Hạng mục | Trạng thái | Bằng chứng / giới hạn |
|---|---|---|
| Backend .NET, SQLite migration/seed | DONE | Build 0 lỗi; khởi động API trên cổng 5000 và test trên SQLite riêng cổng 5011 |
| OTP, quên/đặt lại/đổi mật khẩu, JWT, phân quyền | DONE | API smoke thử toàn bộ luồng, rotation, khóa tài khoản có hiệu lực ngay; app đăng nhập Admin và Employee |
| CRUD API nhân viên/phòng ban/ca/phân ca/nghỉ | DONE | API smoke thử tạo/sửa/xóa và duyệt nghỉ; chưa bao phủ mọi dữ liệu sai |
| Check-in/out Backend, GPS, QR một lần | DONE | API smoke thử ngoài vùng, tái sử dụng QR, Check-in và Check-out; app thực hiện thành công trên Pixel_7 Emulator. [Ảnh lịch sử](emulator-attendance-history.png) |
| Android Compose, Retrofit, dashboard theo vai trò | DONE | `assembleDebug` thành công; Admin có số liệu hôm nay/hoạt động gần đây, Employee có ca/giờ vào ra, Customer có hồ sơ riêng |
| Smart Attendance Assistant | DONE | Cảnh báo theo ca và trạng thái, đúng giờ/về sớm/tổng giờ/xu hướng 7 ngày-tháng, lối tắt yêu cầu chỉnh công; đã hiển thị trên Emulator. [Ảnh](emulator-smart-assistant.png) |
| BiometricPrompt và GPS Android | DONE | Đăng ký fingerprint Emulator, xác thực và lấy GPS để Check-in/Check-out thành công; từ chối khi chưa có fingerprint |
| Hình QR, lưu/chọn ảnh và ML Kit giải mã ảnh | DONE | Admin tạo/lưu QR; Employee chọn ảnh và Check-in/Check-out thành công |
| CameraX + ML Kit quét QR trực tiếp | UNVERIFIED | Preview CameraX mở trên Emulator; chưa thử đưa QR thật trước camera và xác nhận giải mã trực tiếp |
| Android quản trị CRUD, người dùng, đơn từ, báo cáo/thông báo | UNVERIFIED | Màn người dùng đã tải dữ liệu thật trên Emulator ([ảnh](emulator-admin-users.png)); API smoke PASS, chưa thử toàn bộ thao tác CRUD từ UI |
| Hồ sơ và bảo mật Android | DONE | Admin mở hồ sơ thật trên Emulator; API smoke thử cập nhật hồ sơ, quên/đặt lại và đổi mật khẩu |
| Keystore và refresh token Android | DONE | Đóng hẳn rồi mở lại app vẫn vào dashboard; API `/auth/refresh` trả 200; API smoke thử rotation |
| Launcher demo một lần nhấp | DONE | `start-demo.ps1` build, khởi động Backend, cài/mở APK trên Emulator thành công |
| Test tự động API | DONE | `tests/api-smoke.ps1` PASS với DB riêng: OTP/reset, hồ sơ, quản lý user, quyền, CRUD, dashboard, GPS, QR, chấm công, refresh |
| Customer dashboard và hồ sơ | DONE | Đăng nhập Customer qua OTP demo; thông tin công ty, thông báo công khai và hồ sơ bảo mật hiển thị trên Emulator |
| SMTP production, release HTTPS | UNVERIFIED | Có cấu hình SMTP, chưa thử SMTP thật; APK release chưa cấu hình server HTTPS |
| Hilt, Navigation Compose, DataStore, push notification | MISSING | Hiện dùng Compose state và Keystore/SharedPreferences; thông báo trạng thái đơn được tải trong app, chưa có push |

Ứng dụng demo hiện có đủ ba bước chấm công (vân tay, GPS, QR) và không cần nhập token. Trước khi triển khai thật cần thử camera trên thiết bị thật, luồng UI quản trị/đăng ký đầy đủ, SMTP và cấu hình HTTPS.
