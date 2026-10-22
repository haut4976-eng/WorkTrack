using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using System.Net;
using System.Net.Mail;
using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using System.Collections.Concurrent;

namespace WorkTrack.Api;

public sealed record JwtSettings(string Key);
public sealed record RegisterDto(string FullName, string Username, string Email, string? Phone, string Password, string ConfirmPassword);
public sealed record LoginDto(string Username, string Password);
public sealed record ForgotPasswordDto(string Username);
public sealed record OtpDto(string Username, string Code);
public sealed record ResetDto(string Username, string Code, string NewPassword);
public sealed record RefreshDto(string RefreshToken);
public sealed record ProfileDto(string FullName, string Email, string? Phone);
public sealed record ChangePasswordDto(string CurrentPassword, string NewPassword);
public sealed record UpdateUserDto(string Role, bool Active);

public sealed class DemoOtpStore
{
    private readonly ConcurrentDictionary<string, string> values = new();
    public void Set(string username, string purpose, string code) => values[$"{username.ToLowerInvariant()}:{purpose.ToUpperInvariant()}"] = code;
    public string? Get(string username, string purpose) => values.TryGetValue($"{username.ToLowerInvariant()}:{purpose.ToUpperInvariant()}", out var code) ? code : null;
    public void Remove(string username, string purpose) => values.TryRemove($"{username.ToLowerInvariant()}:{purpose.ToUpperInvariant()}", out _);
}

public sealed class AuthService(AppDb db, JwtSettings jwt, ILogger<AuthService> logger, IWebHostEnvironment env, DemoOtpStore demoOtps)
{
    private readonly PasswordHasher<User> hasher = new();
    public async Task<IResult> Register(RegisterDto x)
    {
        var username = x.Username.Trim().ToLowerInvariant();
        var email = x.Email.Trim().ToLowerInvariant();
        if (string.IsNullOrWhiteSpace(x.FullName) || username.Length < 3 || !new System.ComponentModel.DataAnnotations.EmailAddressAttribute().IsValid(email) || !Strong(x.Password) || x.Password != x.ConfirmPassword)
            return Results.BadRequest(new { error = "Thông tin đăng ký không hợp lệ; mật khẩu cần ít nhất 8 ký tự, chữ hoa, chữ thường và số." });
        if (await db.Users.AnyAsync(u => u.Username == username || u.Email == email)) return Results.Conflict(new { error = "Username hoặc email đã tồn tại." });
        var user = new User { FullName = x.FullName.Trim(), Username = username, Email = email, Phone = x.Phone, Role = "CUSTOMER" };
        user.PasswordHash = hasher.HashPassword(user, x.Password);
        db.Users.Add(user);
        await db.SaveChangesAsync();
        await IssueOtp(user, "REGISTER");
        return Results.Accepted(value: new { message = "Nhập OTP để kích hoạt tài khoản." });
    }
    public async Task<IResult> VerifyRegistration(OtpDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is null || !await VerifyOtp(user.Id, "REGISTER", x.Code)) return Results.BadRequest(new { error = "OTP không hợp lệ hoặc đã hết hạn." });
        user.Verified = true;
        await db.SaveChangesAsync();
        return Results.Ok(new { message = "Tài khoản đã được kích hoạt." });
    }
    public async Task<IResult> Login(LoginDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is null || !user.Active || !user.Verified || hasher.VerifyHashedPassword(user, user.PasswordHash, x.Password) == PasswordVerificationResult.Failed)
            return Results.Unauthorized();
        await IssueOtp(user, "LOGIN");
        return Results.Accepted(value: new { message = "Nhập OTP đã gửi tới email.", username = user.Username });
    }
    public async Task<IResult> VerifyLogin(OtpDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is null || !user.Active || !user.Verified || !await VerifyOtp(user.Id, "LOGIN", x.Code)) return Results.BadRequest(new { error = "OTP không hợp lệ hoặc đã hết hạn." });
        return Results.Ok(await Tokens(user));
    }
    public async Task<IResult> Resend(OtpDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is null) return Results.Accepted();
        var purpose = user.Verified ? "LOGIN" : "REGISTER";
        var last = await db.OtpChallenges.Where(o => o.UserId == user.Id && o.Purpose == purpose).OrderByDescending(o => o.Id).FirstOrDefaultAsync();
        if (last is not null && last.ExpiresUtc > DateTime.UtcNow.AddMinutes(4)) return Results.StatusCode(429);
        await IssueOtp(user, purpose);
        return Results.Accepted();
    }
    public async Task<IResult> Forgot(ForgotPasswordDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is not null && user.Active) await IssueOtp(user, "RESET");
        return Results.Accepted(value: new { message = "Nếu tài khoản tồn tại, OTP đã được gửi." });
    }
    public async Task<IResult> Reset(ResetDto x)
    {
        var user = await db.Users.SingleOrDefaultAsync(u => u.Username == x.Username.ToLower());
        if (user is null || !Strong(x.NewPassword) || !await VerifyOtp(user.Id, "RESET", x.Code)) return Results.BadRequest(new { error = "Yêu cầu không hợp lệ." });
        user.PasswordHash = hasher.HashPassword(user, x.NewPassword);
        await db.RefreshTokens.Where(t => t.UserId == user.Id && t.RevokedUtc == null).ExecuteUpdateAsync(s => s.SetProperty(t => t.RevokedUtc, DateTime.UtcNow));
        await db.SaveChangesAsync();
        return Results.Ok(new { message = "Mật khẩu đã đổi." });
    }
    public async Task<IResult> Refresh(RefreshDto x)
    {
        var hash = Hash(x.RefreshToken);
        var old = await db.RefreshTokens.SingleOrDefaultAsync(t => t.Hash == hash);
        if (old is null || old.RevokedUtc is not null || old.ExpiresUtc < DateTime.UtcNow) return Results.Unauthorized();
        var user = await db.Users.FindAsync(old.UserId);
        if (user is null || !user.Active) return Results.Unauthorized();
        old.RevokedUtc = DateTime.UtcNow;
        return Results.Ok(await Tokens(user));
    }
    public async Task<IResult> Logout(RefreshDto x, ClaimsPrincipal principal)
    {
        var id = int.Parse(principal.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var hash = Hash(x.RefreshToken);
        var token = await db.RefreshTokens.SingleOrDefaultAsync(t => t.Hash == hash && t.UserId == id);
        if (token is not null) { token.RevokedUtc = DateTime.UtcNow; await db.SaveChangesAsync(); }
        return Results.NoContent();
    }
    public async Task<IResult> UpdateProfile(ProfileDto x, ClaimsPrincipal principal)
    {
        var id = int.Parse(principal.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var fullName = x.FullName.Trim();
        var email = x.Email.Trim().ToLowerInvariant();
        var phone = string.IsNullOrWhiteSpace(x.Phone) ? null : x.Phone.Trim();
        if (fullName.Length < 2 || !new System.ComponentModel.DataAnnotations.EmailAddressAttribute().IsValid(email))
            return Results.BadRequest(new { error = "Họ tên hoặc email không hợp lệ." });
        if (await db.Users.AnyAsync(u => u.Id != id && u.Email == email))
            return Results.Conflict(new { error = "Email đã được tài khoản khác sử dụng." });
        var user = await db.Users.FindAsync(id);
        if (user is null) return Results.NotFound();
        user.FullName = fullName;
        user.Email = email;
        user.Phone = phone;
        var employee = await db.Employees.SingleOrDefaultAsync(e => e.UserId == id);
        if (employee is not null) { employee.FullName = fullName; employee.Email = email; employee.Phone = phone; }
        await db.SaveChangesAsync();
        return Results.Ok(new { user.Id, user.Username, user.FullName, user.Email, user.Phone, user.Role });
    }
    public async Task<IResult> ChangePassword(ChangePasswordDto x, ClaimsPrincipal principal)
    {
        var id = int.Parse(principal.FindFirstValue(ClaimTypes.NameIdentifier)!);
        var user = await db.Users.FindAsync(id);
        if (user is null) return Results.NotFound();
        if (hasher.VerifyHashedPassword(user, user.PasswordHash, x.CurrentPassword) == PasswordVerificationResult.Failed)
            return Results.BadRequest(new { error = "Mật khẩu hiện tại không đúng." });
        if (!Strong(x.NewPassword))
            return Results.BadRequest(new { error = "Mật khẩu mới cần ít nhất 8 ký tự, chữ hoa, chữ thường và số." });
        user.PasswordHash = hasher.HashPassword(user, x.NewPassword);
        await db.SaveChangesAsync();
        return Results.Ok(new { message = "Đã đổi mật khẩu." });
    }
    private async Task<string> IssueOtp(User user, string purpose)
    {
        var code = RandomNumberGenerator.GetInt32(100000, 1000000).ToString();
        db.OtpChallenges.Add(new OtpChallenge { UserId = user.Id, Purpose = purpose, Hash = Hash(code), ExpiresUtc = DateTime.UtcNow.AddMinutes(5) });
        await db.SaveChangesAsync();
        if (env.IsDevelopment() && Environment.GetEnvironmentVariable("WORKTRACK_DEMO_OTP") == "1") demoOtps.Set(user.Username, purpose, code);
        if (env.IsDevelopment()) logger.LogWarning("Development OTP for {Username} ({Purpose}): {Code}", user.Username, purpose, code);
        else
        {
            var host = Environment.GetEnvironmentVariable("WORKTRACK_SMTP_HOST") ?? throw new InvalidOperationException("Configure WORKTRACK_SMTP_HOST.");
            var from = Environment.GetEnvironmentVariable("WORKTRACK_SMTP_FROM") ?? throw new InvalidOperationException("Configure WORKTRACK_SMTP_FROM.");
            using var smtp = new SmtpClient(host, int.TryParse(Environment.GetEnvironmentVariable("WORKTRACK_SMTP_PORT"), out var port) ? port : 587)
            { EnableSsl = true, Credentials = new NetworkCredential(Environment.GetEnvironmentVariable("WORKTRACK_SMTP_USER"), Environment.GetEnvironmentVariable("WORKTRACK_SMTP_PASSWORD")) };
            using var mail = new MailMessage(from, user.Email, "WorkTrack OTP", $"Mã xác minh {purpose}: {code}. Có hiệu lực 5 phút.");
            await smtp.SendMailAsync(mail);
        }
        return code;
    }
    private async Task<bool> VerifyOtp(int id, string purpose, string code)
    {
        var otp = await db.OtpChallenges.Where(o => o.UserId == id && o.Purpose == purpose && o.UsedUtc == null).OrderByDescending(o => o.Id).FirstOrDefaultAsync();
        if (otp is null || otp.ExpiresUtc < DateTime.UtcNow || otp.Attempts >= 5) return false;
        otp.Attempts++;
        var valid = CryptographicOperations.FixedTimeEquals(Convert.FromHexString(otp.Hash), Convert.FromHexString(Hash(code)));
        if (valid) otp.UsedUtc = DateTime.UtcNow;
        await db.SaveChangesAsync();
        if (valid) demoOtps.Remove((await db.Users.FindAsync(id))?.Username ?? "", purpose);
        return valid;
    }
    private async Task<object> Tokens(User user)
    {
        var claims = new[] { new Claim(ClaimTypes.NameIdentifier, user.Id.ToString()), new Claim(ClaimTypes.Role, user.Role), new Claim(ClaimTypes.Name, user.Username) };
        var credentials = new SigningCredentials(new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwt.Key)), SecurityAlgorithms.HmacSha256);
        var access = new JwtSecurityToken("WorkTrack", "WorkTrack.Android", claims, expires: DateTime.UtcNow.AddMinutes(30), signingCredentials: credentials);
        var refresh = Convert.ToBase64String(RandomNumberGenerator.GetBytes(48));
        db.RefreshTokens.Add(new RefreshToken { UserId = user.Id, Hash = Hash(refresh), ExpiresUtc = DateTime.UtcNow.AddDays(14) });
        await db.SaveChangesAsync();
        return new { accessToken = new JwtSecurityTokenHandler().WriteToken(access), refreshToken = refresh, role = user.Role, fullName = user.FullName };
    }
    private static bool Strong(string x) => x.Length >= 8 && x.Any(char.IsUpper) && x.Any(char.IsLower) && x.Any(char.IsDigit);
    public static string Hash(string x) => Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(x)));
}

public static class AuthEndpoints
{
    public static void MapAuth(this WebApplication app)
    {
        if (app.Environment.IsDevelopment() && Environment.GetEnvironmentVariable("WORKTRACK_DEMO_OTP") == "1")
            app.MapGet("/api/v1/dev/otp", (string username, string purpose, DemoOtpStore store) => store.Get(username, purpose) is { } code ? Results.Ok(new { code }) : Results.NotFound()).RequireRateLimiting("auth");
        var g = app.MapGroup("/api/v1/auth").RequireRateLimiting("auth");
        g.MapPost("/register", (RegisterDto x, AuthService s) => s.Register(x));
        g.MapPost("/verify-registration", (OtpDto x, AuthService s) => s.VerifyRegistration(x));
        g.MapPost("/login", (LoginDto x, AuthService s) => s.Login(x));
        g.MapPost("/verify-login-otp", (OtpDto x, AuthService s) => s.VerifyLogin(x));
        g.MapPost("/resend-otp", (OtpDto x, AuthService s) => s.Resend(x));
        g.MapPost("/forgot-password", (ForgotPasswordDto x, AuthService s) => s.Forgot(x));
        g.MapPost("/reset-password", (ResetDto x, AuthService s) => s.Reset(x));
        g.MapPost("/refresh", (RefreshDto x, AuthService s) => s.Refresh(x));
        g.MapPost("/logout", (RefreshDto x, ClaimsPrincipal p, AuthService s) => s.Logout(x, p)).RequireAuthorization();
        g.MapGet("/me", async (ClaimsPrincipal p, AppDb db) =>
        {
            var id = int.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
            return await db.Users.Where(u => u.Id == id).Select(u => new { u.Id, u.Username, u.FullName, u.Email, u.Phone, u.Role }).SingleAsync();
        }).RequireAuthorization();
        g.MapPut("/me", (ProfileDto x, ClaimsPrincipal p, AuthService s) => s.UpdateProfile(x, p)).RequireAuthorization();
        g.MapPost("/change-password", (ChangePasswordDto x, ClaimsPrincipal p, AuthService s) => s.ChangePassword(x, p)).RequireAuthorization();

        var users = app.MapGroup("/api/v1/users").RequireAuthorization("ADMIN");
        users.MapGet("/", async (AppDb db) => await db.Users.OrderBy(u => u.Username).Select(u => new { u.Id, u.Username, u.FullName, u.Email, u.Role, u.Active, u.Verified }).ToListAsync());
        users.MapPut("/{id:int}", async (int id, UpdateUserDto x, ClaimsPrincipal principal, AppDb db) =>
        {
            var roles = new[] { "ADMIN", "EMPLOYEE", "CUSTOMER" };
            var role = x.Role.Trim().ToUpperInvariant();
            if (!roles.Contains(role)) return Results.BadRequest(new { error = "Vai trò không hợp lệ." });
            var actorId = int.Parse(principal.FindFirstValue(ClaimTypes.NameIdentifier)!);
            var user = await db.Users.FindAsync(id);
            if (user is null) return Results.NotFound();
            if (id == actorId && (!x.Active || role != "ADMIN")) return Results.BadRequest(new { error = "Không thể khóa hoặc hạ quyền tài khoản Admin đang đăng nhập." });
            if (user.Role == "ADMIN" && (role != "ADMIN" || !x.Active) && await db.Users.CountAsync(u => u.Role == "ADMIN" && u.Active) <= 1)
                return Results.BadRequest(new { error = "Hệ thống phải còn ít nhất một Admin hoạt động." });
            var employee = await db.Employees.SingleOrDefaultAsync(e => e.UserId == id);
            if (role == "EMPLOYEE" && employee is null) return Results.BadRequest(new { error = "Hãy tạo hồ sơ nhân viên và gắn tài khoản trước." });
            user.Role = role;
            user.Active = x.Active;
            if (employee is not null) employee.Active = x.Active && role == "EMPLOYEE";
            await db.RefreshTokens.Where(t => t.UserId == id && t.RevokedUtc == null).ExecuteUpdateAsync(s => s.SetProperty(t => t.RevokedUtc, DateTime.UtcNow));
            db.AuditLogs.Add(new AuditLog { ActorUserId = actorId, Action = "USER_UPDATE", Subject = id.ToString(), AtUtc = DateTime.UtcNow });
            await db.SaveChangesAsync();
            return Results.Ok(new { user.Id, user.Username, user.FullName, user.Email, user.Role, user.Active, user.Verified });
        });
    }
}
