using Microsoft.EntityFrameworkCore;

namespace WorkTrack.Api;

public sealed class User
{
    public int Id { get; set; }
    public string FullName { get; set; } = "";
    public string Username { get; set; } = "";
    public string Email { get; set; } = "";
    public string? Phone { get; set; }
    public string PasswordHash { get; set; } = "";
    public string Role { get; set; } = "CUSTOMER";
    public bool Verified { get; set; }
    public bool Active { get; set; } = true;
}
public sealed class OtpChallenge
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public string Purpose { get; set; } = "";
    public string Hash { get; set; } = "";
    public DateTime ExpiresUtc { get; set; }
    public int Attempts { get; set; }
    public DateTime? UsedUtc { get; set; }
}
public sealed class RefreshToken
{
    public int Id { get; set; }
    public int UserId { get; set; }
    public string Hash { get; set; } = "";
    public DateTime ExpiresUtc { get; set; }
    public DateTime? RevokedUtc { get; set; }
}
public sealed class Department
{
    public int Id { get; set; }
    public string Code { get; set; } = "";
    public string Name { get; set; } = "";
    public string Description { get; set; } = "";
    public bool Active { get; set; } = true;
}
public sealed class Employee
{
    public int Id { get; set; }
    public string EmployeeCode { get; set; } = "";
    public string FullName { get; set; } = "";
    public string Email { get; set; } = "";
    public string? Phone { get; set; }
    public int DepartmentId { get; set; }
    public string Position { get; set; } = "";
    public DateOnly HireDate { get; set; }
    public bool Active { get; set; } = true;
    public int? UserId { get; set; }
}
public sealed class Shift
{
    public int Id { get; set; }
    public string Name { get; set; } = "";
    public TimeOnly StartTime { get; set; }
    public TimeOnly EndTime { get; set; }
    public int GracePeriodMinutes { get; set; }
    public string Description { get; set; } = "";
    public bool Active { get; set; } = true;
}
public sealed class ShiftAssignment
{
    public int Id { get; set; }
    public int EmployeeId { get; set; }
    public int ShiftId { get; set; }
    public DateOnly WorkDate { get; set; }
}
public sealed class Office
{
    public int Id { get; set; }
    public string Name { get; set; } = "";
    public double Latitude { get; set; }
    public double Longitude { get; set; }
    public double AllowedRadiusMeters { get; set; }
    public bool Active { get; set; } = true;
}
public sealed class QrChallenge
{
    public int Id { get; set; }
    public int OfficeId { get; set; }
    public string Hash { get; set; } = "";
    public DateTime ExpiresUtc { get; set; }
    public DateTime? UsedUtc { get; set; }
}
public sealed class AttendanceRecord
{
    public int Id { get; set; }
    public int EmployeeId { get; set; }
    public int ShiftAssignmentId { get; set; }
    public DateTime CheckInUtc { get; set; }
    public DateTime? CheckOutUtc { get; set; }
    public bool Late { get; set; }
    public bool EarlyLeave { get; set; }
    public int OfficeId { get; set; }
}
public sealed class LeaveRequest
{
    public int Id { get; set; }
    public int EmployeeId { get; set; }
    public DateOnly StartDate { get; set; }
    public DateOnly EndDate { get; set; }
    public string Reason { get; set; } = "";
    public string Status { get; set; } = "PENDING";
    public string? ReviewReason { get; set; }
}
public sealed class CorrectionRequest
{
    public int Id { get; set; }
    public int AttendanceRecordId { get; set; }
    public int EmployeeId { get; set; }
    public DateTime ProposedCheckOutUtc { get; set; }
    public string Reason { get; set; } = "";
    public string Status { get; set; } = "PENDING";
    public string? ReviewReason { get; set; }
}
public sealed class AuditLog
{
    public int Id { get; set; }
    public int ActorUserId { get; set; }
    public string Action { get; set; } = "";
    public string Subject { get; set; } = "";
    public DateTime AtUtc { get; set; }
}
public sealed class AppDb(DbContextOptions<AppDb> options) : DbContext(options)
{
    public DbSet<User> Users => Set<User>();
    public DbSet<OtpChallenge> OtpChallenges => Set<OtpChallenge>();
    public DbSet<RefreshToken> RefreshTokens => Set<RefreshToken>();
    public DbSet<Department> Departments => Set<Department>();
    public DbSet<Employee> Employees => Set<Employee>();
    public DbSet<Shift> Shifts => Set<Shift>();
    public DbSet<ShiftAssignment> ShiftAssignments => Set<ShiftAssignment>();
    public DbSet<Office> Offices => Set<Office>();
    public DbSet<QrChallenge> QrChallenges => Set<QrChallenge>();
    public DbSet<AttendanceRecord> AttendanceRecords => Set<AttendanceRecord>();
    public DbSet<LeaveRequest> LeaveRequests => Set<LeaveRequest>();
    public DbSet<CorrectionRequest> CorrectionRequests => Set<CorrectionRequest>();
    public DbSet<AuditLog> AuditLogs => Set<AuditLog>();
    protected override void OnModelCreating(ModelBuilder b)
    {
        b.Entity<User>().HasIndex(x => x.Username).IsUnique();
        b.Entity<User>().HasIndex(x => x.Email).IsUnique();
        b.Entity<Employee>().HasIndex(x => x.EmployeeCode).IsUnique();
        b.Entity<Employee>().HasIndex(x => x.UserId).IsUnique();
        b.Entity<Department>().HasIndex(x => x.Code).IsUnique();
        b.Entity<ShiftAssignment>().HasIndex(x => new { x.EmployeeId, x.WorkDate }).IsUnique();
        b.Entity<AttendanceRecord>().HasIndex(x => x.ShiftAssignmentId).IsUnique();
        b.Entity<Employee>().HasOne<Department>().WithMany().HasForeignKey(x => x.DepartmentId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<Employee>().HasOne<User>().WithOne().HasForeignKey<Employee>(x => x.UserId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<ShiftAssignment>().HasOne<Employee>().WithMany().HasForeignKey(x => x.EmployeeId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<ShiftAssignment>().HasOne<Shift>().WithMany().HasForeignKey(x => x.ShiftId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<AttendanceRecord>().HasOne<ShiftAssignment>().WithOne().HasForeignKey<AttendanceRecord>(x => x.ShiftAssignmentId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<LeaveRequest>().HasOne<Employee>().WithMany().HasForeignKey(x => x.EmployeeId).OnDelete(DeleteBehavior.Restrict);
        b.Entity<CorrectionRequest>().HasOne<AttendanceRecord>().WithMany().HasForeignKey(x => x.AttendanceRecordId).OnDelete(DeleteBehavior.Restrict);
    }
}
