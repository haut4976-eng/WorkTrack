# API v1 hiện có

Base URL host: `http://localhost:5000/api/v1`; Swagger Development: `/swagger`. Gửi `Authorization: Bearer <accessToken>` sau khi xác minh login OTP. Body JSON dùng camelCase. Lỗi thường gặp: 400 dữ liệu không hợp lệ, 401 chưa xác thực, 403 thiếu quyền, 409 xung đột, 429 quá giới hạn.

| Nhóm | Endpoint | Quyền |
|---|---|---|
| Health | `GET /health` | Public |
| Auth | `POST /auth/register`, `/verify-registration`, `/login`, `/verify-login-otp`, `/resend-otp`, `/forgot-password`, `/reset-password`, `/refresh` | Public |
| Auth | `GET /auth/me`, `POST /auth/logout` | Đăng nhập |
| Employees | `GET /employees`, `GET /employees/{id}`, `POST /employees`, `PUT /employees/{id}`, `DELETE /employees/{id}` | ADMIN |
| Departments | `GET /departments`, `GET /departments/{id}` | Đăng nhập |
| Departments | `POST /departments`, `PUT /departments/{id}`, `DELETE /departments/{id}` | ADMIN |
| Shifts | `GET /shifts` | Đăng nhập |
| Shifts | `POST /shifts`, `PUT /shifts/{id}`, `DELETE /shifts/{id}` | ADMIN |
| Assignment | `GET /shift-assignments` | ADMIN hoặc nhân viên xem ca của mình |
| Assignment | `POST /shift-assignments`, `DELETE /shift-assignments/{id}` | ADMIN |
| Office | `GET /offices` | Đăng nhập |
| Office | `POST /offices`, `PUT /offices/{id}` | ADMIN |
| QR | `POST /qr/generate?officeId=1` | ADMIN |
| QR | `POST /qr/validate?token=...` | EMPLOYEE |
| Attendance | `POST /attendance/check-in`, `/check-out`; `GET /attendance/today`, `/history`, `/summary` | EMPLOYEE |
| Attendance | `GET /attendance/reports`, `/employee/{id}` | ADMIN |
| Leave | `GET /leave-requests`, `POST /leave-requests`, `PUT /leave-requests/{id}`, `DELETE /leave-requests/{id}` | Employee xem/sửa đơn của mình; Admin xem toàn bộ |
| Leave | `POST /leave-requests/{id}/approve`, `/reject` | ADMIN |
| Correction | `GET /correction-requests`, `POST /correction-requests` | EMPLOYEE hoặc ADMIN theo quyền dữ liệu |
| Correction | `POST /correction-requests/{id}/approve`, `/reject` | ADMIN |

Ví dụ login:

```json
{"username":"employee1","password":"DemoPass123!"}
```

`POST /auth/login` trả 202; OTP được gửi email ở production hoặc ghi log Development. OTP không nằm trong response xác thực. Khi `WORKTRACK_DEMO_OTP=1` và môi trường là Development, app đọc mã từ hộp thư giả lập `GET /api/v1/dev/otp?username=...&purpose=LOGIN`; endpoint này không được tạo ở production. `POST /auth/verify-login-otp` trả `accessToken`, `refreshToken`, `role`, `fullName`. Không ghi JWT hoặc refresh token vào log.

Ví dụ check-in:

```json
{"qrToken":"token-vua-phat-hanh","latitude":10.7769,"longitude":106.7009,"accuracyMeters":10,"locationTimestampUtc":"2026-10-22T08:00:00Z"}
```

Thời điểm vị trí phải mới trong vòng 5 phút, sai số <=100m, token chưa dùng và chưa hết hạn. `check-out` cần token mới. Backend dùng thời gian server, không dùng `locationTimestampUtc` làm giờ chấm công. Giá trị timestamp trong ví dụ chỉ minh họa; hãy gửi thời điểm thực tế.
