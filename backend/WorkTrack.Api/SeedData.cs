using Microsoft.AspNetCore.Identity;
using Microsoft.EntityFrameworkCore;

namespace WorkTrack.Api;

public static class SeedData
{
    public static async Task Apply(AppDb db)
    {
        if (await db.Users.AnyAsync()) return;
        var password = Environment.GetEnvironmentVariable("WORKTRACK_DEMO_PASSWORD") ?? "DemoPass123!";
        var hasher = new PasswordHasher<User>();
        User Add(string username, string role, string name)
        {
            var u = new User { Username = username, Email = username + "@worktrack.local", FullName = name, Role = role, Verified = true };
            u.PasswordHash = hasher.HashPassword(u, password);
            db.Users.Add(u);
            return u;
        }
        var admin = Add("admin", "ADMIN", "Quản trị viên");
        var e1 = Add("employee1", "EMPLOYEE", "Nguyễn An");
        var e2 = Add("employee2", "EMPLOYEE", "Trần Bình");
        var e3 = Add("employee3", "EMPLOYEE", "Lê Chi");
        Add("customer", "CUSTOMER", "Khách hàng");
        var d1 = new Department { Code = "DEV", Name = "Phát triển" };
        var d2 = new Department { Code = "HR", Name = "Nhân sự" };
        db.Departments.AddRange(d1, d2);
        await db.SaveChangesAsync();
        var employees = new[] {
            new Employee { EmployeeCode = "E001", FullName = e1.FullName, Email = e1.Email, DepartmentId = d1.Id, Position = "Developer", HireDate = DateOnly.FromDateTime(DateTime.Today.AddYears(-1)), UserId = e1.Id },
            new Employee { EmployeeCode = "E002", FullName = e2.FullName, Email = e2.Email, DepartmentId = d1.Id, Position = "Tester", HireDate = DateOnly.FromDateTime(DateTime.Today.AddMonths(-8)), UserId = e2.Id },
            new Employee { EmployeeCode = "E003", FullName = e3.FullName, Email = e3.Email, DepartmentId = d2.Id, Position = "HR", HireDate = DateOnly.FromDateTime(DateTime.Today.AddMonths(-6)), UserId = e3.Id }
        };
        db.Employees.AddRange(employees);
        var shifts = new[] {
            new Shift { Name = "Ca sáng", StartTime = new TimeOnly(8,0), EndTime = new TimeOnly(17,0), GracePeriodMinutes = 10 },
            new Shift { Name = "Ca chiều", StartTime = new TimeOnly(13,0), EndTime = new TimeOnly(21,0), GracePeriodMinutes = 10 },
            new Shift { Name = "Ca đêm", StartTime = new TimeOnly(22,0), EndTime = new TimeOnly(6,0), GracePeriodMinutes = 10 }
        };
        db.Shifts.AddRange(shifts);
        db.Offices.Add(new Office { Name = "Văn phòng demo", Latitude = 10.7769, Longitude = 106.7009, AllowedRadiusMeters = 250 });
        await db.SaveChangesAsync();
        var today = DateOnly.FromDateTime(TimeZoneInfo.ConvertTimeFromUtc(DateTime.UtcNow, TimeZoneInfo.FindSystemTimeZoneById("SE Asia Standard Time")));
        foreach (var employee in employees)
            for (var day = -5; day <= 1; day++)
                db.ShiftAssignments.Add(new ShiftAssignment { EmployeeId = employee.Id, ShiftId = shifts[0].Id, WorkDate = today.AddDays(day) });
        await db.SaveChangesAsync();
        db.LeaveRequests.Add(new LeaveRequest { EmployeeId = employees[2].Id, StartDate = today.AddDays(2), EndDate = today.AddDays(2), Reason = "Việc cá nhân" });
        await db.SaveChangesAsync();
    }
}
