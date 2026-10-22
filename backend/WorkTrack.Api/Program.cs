using System.Security.Claims;
using System.Text;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.AspNetCore.DataProtection;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;
using WorkTrack.Api;

var builder = WebApplication.CreateBuilder(args);
builder.Logging.ClearProviders().AddConsole();
builder.Services.AddDataProtection().PersistKeysToFileSystem(new DirectoryInfo(Path.Combine(builder.Environment.ContentRootPath, ".keys")));
var jwtKey = Environment.GetEnvironmentVariable("WORKTRACK_JWT_KEY");
if (string.IsNullOrWhiteSpace(jwtKey))
{
    if (!builder.Environment.IsDevelopment()) throw new InvalidOperationException("Set WORKTRACK_JWT_KEY (at least 32 characters).");
    jwtKey = Convert.ToBase64String(System.Security.Cryptography.RandomNumberGenerator.GetBytes(48));
    Console.WriteLine("Development JWT key generated for this process; sessions expire on restart.");
}
if (Encoding.UTF8.GetByteCount(jwtKey) < 32) throw new InvalidOperationException("WORKTRACK_JWT_KEY must have at least 32 UTF-8 bytes.");
builder.Services.AddSingleton(new JwtSettings(jwtKey));
builder.Services.AddSingleton<DemoOtpStore>();
builder.Services.AddDbContext<AppDb>(o => o.UseSqlite(builder.Configuration.GetConnectionString("Default") ?? "Data Source=worktrack-v1.db"));
builder.Services.AddScoped<AuthService>();
builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme).AddJwtBearer(o =>
{
    o.TokenValidationParameters = new TokenValidationParameters
    {
        ValidateIssuer = true, ValidIssuer = "WorkTrack",
        ValidateAudience = true, ValidAudience = "WorkTrack.Android",
        ValidateLifetime = true, ValidateIssuerSigningKey = true,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtKey)),
        ClockSkew = TimeSpan.FromSeconds(10)
    };
});
builder.Services.AddAuthorization(o =>
{
    o.AddPolicy("ADMIN", p => p.RequireRole("ADMIN"));
    o.AddPolicy("EMPLOYEE", p => p.RequireRole("EMPLOYEE"));
});
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddProblemDetails();
builder.Services.AddRateLimiter(o =>
{
    o.RejectionStatusCode = 429;
    o.AddFixedWindowLimiter("auth", x => { x.PermitLimit = builder.Environment.IsDevelopment() ? 30 : 10; x.Window = TimeSpan.FromMinutes(1); x.QueueLimit = 0; });
});
var app = builder.Build();
app.UseExceptionHandler();
app.UseRateLimiter();
app.UseAuthentication();
app.Use(async (context, next) =>
{
    if (context.User.Identity?.IsAuthenticated == true)
    {
        var rawId = context.User.FindFirstValue(ClaimTypes.NameIdentifier);
        var tokenRole = context.User.FindFirstValue(ClaimTypes.Role);
        var db = context.RequestServices.GetRequiredService<AppDb>();
        var user = int.TryParse(rawId, out var id) ? await db.Users.AsNoTracking().SingleOrDefaultAsync(u => u.Id == id) : null;
        if (user is null || !user.Active || user.Role != tokenRole) { context.Response.StatusCode = StatusCodes.Status401Unauthorized; return; }
    }
    await next();
});
app.UseAuthorization();
if (app.Environment.IsDevelopment()) { app.UseSwagger(); app.UseSwaggerUI(); }
app.MapGet("/api/v1/health", () => Results.Ok(new { status = "ok" }));
app.MapAuth();
app.MapBusiness();
using (var scope = app.Services.CreateScope())
{
    var db = scope.ServiceProvider.GetRequiredService<AppDb>();
    await db.Database.MigrateAsync();
    if (app.Environment.IsDevelopment()) await SeedData.Apply(db);
}
app.Run(builder.Configuration["urls"] ?? "http://0.0.0.0:5000");

public partial class Program { }
