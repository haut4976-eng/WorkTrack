# WorkTrack — chấm công Android

Ứng dụng Android Kotlin/Jetpack Compose kết nối Backend ASP.NET Core 8 và SQLite. Nhân viên đã được Admin gắn tài khoản có thể bấm **Check-in** hoặc **Check-out**; app tự xác thực vân tay, lấy GPS và mở camera quét QR. Nhân viên không nhập token. QR do Admin phát hành, hiệu lực 5 phút, dùng một lần. App cũng hỗ trợ chọn ảnh QR để demo trên một Emulator.

## Mở demo bằng một lần nhấp

Máy Windows cần cài .NET SDK 8, Android SDK/Emulator và JDK 17. Nhấp đúp [START_DEMO.cmd](START_DEMO.cmd). Script tự build Backend/APK, khởi động Backend ở cổng 5000 khi cần, cài và mở app trên Emulator. Nếu chưa có Emulator đang chạy, script mở AVD đầu tiên đã tạo trong Android Studio. Lần build đầu cần Internet để tải gói.

Tài khoản demo: `admin`, `employee1`, `employee2`, `employee3`, `customer`; mật khẩu chung `DemoPass123!`. Khi script tự khởi động Backend, chế độ Development tự xác minh OTP trong app để demo không phải đọc log hoặc nhập mã. Chế độ này chỉ bật khi `WORKTRACK_DEMO_OTP=1` và Backend chạy Development. Backend chạy sẵn ở cổng 5000 có thể không bật chế độ demo; khi đó OTP được gửi email hoặc ghi vào log Backend.

## Demo chấm công trên một Emulator

1. Trong Emulator, đặt GPS tại văn phòng demo: vĩ độ `10.7769`, kinh độ `106.7009` trong **Extended controls → Location**. Bật quyền vị trí chính xác.
2. Đăng ký vân tay trong **Android Settings → Security & privacy → Device unlock → Fingerprint**. Khi app hỏi xác thực, gửi dấu vân tay qua **Extended controls → Fingerprint**.
3. Đăng nhập `admin`, ở **Tổng quan** bấm **Tạo QR · Văn phòng demo**, rồi **Lưu ảnh QR để demo trên một Emulator**.
4. Đăng xuất, đăng nhập `employee2`, bấm **Check-in**, xác thực vân tay, đợi GPS, rồi bấm **Chọn ảnh QR từ máy (demo)** và chọn ảnh mới nhất. Nếu dùng hai thiết bị, đưa camera vào QR trên máy Admin.
5. Để Check-out, Admin tạo **QR mới**, rồi Employee bấm **Check-out** và quét mã mới. Dashboard và lịch sử cập nhật sau khi API ghi thành công.

QR dùng một lần; nếu hết hạn hoặc đã dùng, Admin tạo mã mới. GPS phải chính xác trong 100 m và vị trí phải nằm trong bán kính văn phòng. Tài khoản Employee phải còn hoạt động và được Admin gắn vào danh sách nhân viên. Tài khoản tự đăng ký ban đầu là Customer; Admin mở **Nhân viên** và thêm bằng email đã xác minh để cấp quyền Employee.

## Các màn hình

- **Admin:** tổng quan số liệu hôm nay, QR, quản lý nhân viên/phòng ban/ca/phân ca/văn phòng/người dùng, duyệt đơn, báo cáo và hồ sơ bảo mật.
- **Employee:** Check-in/out, ca hôm nay, Smart Attendance Assistant, thống kê/lịch sử, xin nghỉ, yêu cầu bổ sung giờ ra, thông báo và hồ sơ bảo mật.
- **Customer:** dashboard riêng, chỉnh hồ sơ, đổi mật khẩu và giới hạn quyền.

Màn đăng nhập có đăng ký, xác minh OTP, quên mật khẩu và đặt lại mật khẩu. Trong chế độ demo, OTP được tự điền/xác minh để người chấm không phải đọc log hoặc chạy thêm lệnh.

Phiên đăng nhập được mã hóa bằng Android Keystore; app khôi phục phiên sau khi mở lại và xoay refresh token. Đăng xuất thu hồi refresh token và xóa phiên trên thiết bị.

## Kiểm thử và giới hạn

Chạy `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tests\api-smoke.ps1` nếu muốn kiểm thử tự động. Script dùng SQLite riêng và cổng 5011, kiểm tra OTP, quyền, CRUD, phân ca, duyệt nghỉ, QR/GPS, Check-in/out và refresh rotation. Xem [tiến độ và phần chưa xác minh](docs/implementation-progress.md) cùng [kịch bản demo](docs/demo-scenarios.md).

Đã thử Check-in và Check-out end-to-end trên Pixel_7 Emulator bằng ảnh QR; CameraX hiển thị preview và ML Kit đã tích hợp, nhưng chưa xác nhận quét trực tiếp từ camera thật. SMTP production, push notification và release HTTPS chưa được thử triển khai thực tế. Sinh trắc học được xác minh trên thiết bị; Backend không nhận bằng chứng mật mã về vân tay. GPS từ client có thể bị giả lập.
