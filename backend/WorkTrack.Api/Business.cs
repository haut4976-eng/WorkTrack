using System.Security.Claims;
using Microsoft.EntityFrameworkCore;

namespace WorkTrack.Api;

public sealed record DepartmentDto(string Code, string Name, string Description);
public sealed record EmployeeDto(string EmployeeCode, string FullName, string Email, string? Phone, int DepartmentId, string Position, DateOnly HireDate, int? UserId);
public sealed record ShiftDto(string Name, TimeOnly StartTime, TimeOnly EndTime, int GracePeriodMinutes, string Description);
public sealed record AssignmentDto(int EmployeeId, int ShiftId, DateOnly WorkDate);
public sealed record OfficeDto(string Name, double Latitude, double Longitude, double AllowedRadiusMeters);
public sealed record CheckDto(string QrToken, double Latitude, double Longitude, double AccuracyMeters, DateTime LocationTimestampUtc);
public sealed record LeaveDto(DateOnly StartDate, DateOnly EndDate, string Reason);
public sealed record ReviewDto(string? Reason);
public sealed record CorrectionDto(int AttendanceRecordId, DateTime ProposedCheckOutUtc, string Reason);

public static class BusinessEndpoints
{
    private static int Id(ClaimsPrincipal p) => int.Parse(p.FindFirstValue(ClaimTypes.NameIdentifier)!);
    private static async Task<Employee?> OwnEmployee(AppDb db, ClaimsPrincipal p) => await db.Employees.SingleOrDefaultAsync(e => e.UserId == Id(p) && e.Active);
    private static readonly TimeZoneInfo Zone = TimeZoneInfo.FindSystemTimeZoneById(OperatingSystem.IsWindows() ? "SE Asia Standard Time" : "Asia/Ho_Chi_Minh");
    private static DateOnly LocalDay(DateTime utc) => DateOnly.FromDateTime(TimeZoneInfo.ConvertTimeFromUtc(utc, Zone));
    private static DateTime ShiftUtc(DateOnly date, TimeOnly time) => TimeZoneInfo.ConvertTimeToUtc(date.ToDateTime(time), Zone);
    private static double Meters(double a, double b, double c, double d)
    {
        var lat=(c-a)*Math.PI/180;var lon=(d-b)*Math.PI/180;
        var h=Math.Pow(Math.Sin(lat/2),2)+Math.Cos(a*Math.PI/180)*Math.Cos(c*Math.PI/180)*Math.Pow(Math.Sin(lon/2),2);
        return 6371000*2*Math.Atan2(Math.Sqrt(h),Math.Sqrt(1-h));
    }
    private static bool FreshLocation(CheckDto x,DateTime now) =>
        double.IsFinite(x.Latitude)&&double.IsFinite(x.Longitude)&&x.Latitude is >= -90 and <= 90&&x.Longitude is >= -180 and <= 180&&
        double.IsFinite(x.AccuracyMeters)&&x.AccuracyMeters is >= 0 and <= 100&&
        Math.Abs((now-x.LocationTimestampUtc.ToUniversalTime()).TotalMinutes)<=5;
    public static void MapBusiness(this WebApplication app)
    {
        var api = app.MapGroup("/api/v1").RequireAuthorization();
        var admin = api.MapGroup("").RequireAuthorization("ADMIN");
        var dep = api.MapGroup("/departments");
        dep.MapGet("/", async (AppDb db) => await db.Departments.Where(x => x.Active).OrderBy(x => x.Name).ToListAsync());
        dep.MapGet("/{id:int}", async (int id, AppDb db) => await db.Departments.FindAsync(id) is { } x ? Results.Ok(x) : Results.NotFound());
        dep.MapPost("/", async (DepartmentDto x, AppDb db) => { if (string.IsNullOrWhiteSpace(x.Code) || string.IsNullOrWhiteSpace(x.Name)) return Results.BadRequest(); var d = new Department { Code=x.Code.Trim().ToUpper(), Name=x.Name.Trim(), Description=x.Description }; db.Departments.Add(d); await db.SaveChangesAsync(); return Results.Created($"/api/v1/departments/{d.Id}",d); }).RequireAuthorization("ADMIN");
        dep.MapPut("/{id:int}", async (int id, DepartmentDto x, AppDb db) => { var d=await db.Departments.FindAsync(id); if(d is null)return Results.NotFound(); d.Code=x.Code.Trim().ToUpper(); d.Name=x.Name.Trim(); d.Description=x.Description; await db.SaveChangesAsync(); return Results.Ok(d); }).RequireAuthorization("ADMIN");
        dep.MapDelete("/{id:int}", async (int id, AppDb db) => { var d=await db.Departments.FindAsync(id); if(d is null)return Results.NotFound(); d.Active=false; await db.SaveChangesAsync(); return Results.NoContent(); }).RequireAuthorization("ADMIN");

        var emp = api.MapGroup("/employees").RequireAuthorization("ADMIN");
        emp.MapGet("/", async (string? search, int? departmentId, bool? active, int? page, AppDb db) => await db.Employees.Where(e => (!active.HasValue || e.Active==active) && (!departmentId.HasValue || e.DepartmentId==departmentId) && (string.IsNullOrEmpty(search) || e.FullName.Contains(search) || e.EmployeeCode.Contains(search))).OrderBy(e=>e.Id).Skip((Math.Max(1,page??1)-1)*20).Take(20).ToListAsync());
        emp.MapGet("/{id:int}", async (int id, AppDb db) => await db.Employees.FindAsync(id) is { } e ? Results.Ok(e) : Results.NotFound());
        emp.MapPost("/", async (EmployeeDto x, AppDb db) =>
        {
            if(string.IsNullOrWhiteSpace(x.EmployeeCode)||string.IsNullOrWhiteSpace(x.FullName)||!await db.Departments.AnyAsync(d=>d.Id==x.DepartmentId&&d.Active))return Results.BadRequest(new{error="Dữ liệu nhân viên hoặc phòng ban không hợp lệ."});
            var user=x.UserId.HasValue?await db.Users.FindAsync(x.UserId.Value):await db.Users.SingleOrDefaultAsync(u=>u.Email==x.Email.Trim().ToLower());
            if(user is null||!user.Active||!user.Verified||user.Role=="ADMIN")return Results.BadRequest(new{error="Email phải thuộc tài khoản đã xác minh và đang hoạt động."});
            if(await db.Employees.AnyAsync(e=>e.UserId==user.Id))return Results.Conflict(new{error="Tài khoản đã được gắn với nhân viên khác."});
            var e=new Employee { EmployeeCode=x.EmployeeCode.Trim(),FullName=x.FullName.Trim(),Email=user.Email,Phone=x.Phone,DepartmentId=x.DepartmentId,Position=x.Position,HireDate=x.HireDate,UserId=user.Id };
            user.Role="EMPLOYEE";db.Employees.Add(e);
            try{await db.SaveChangesAsync();}catch(DbUpdateException){return Results.Conflict(new{error="Mã hoặc tài khoản nhân viên đã tồn tại."});}
            return Results.Created($"/api/v1/employees/{e.Id}",e);
        });
        emp.MapPut("/{id:int}", async (int id, EmployeeDto x, AppDb db) =>
        {
            var e=await db.Employees.FindAsync(id);if(e is null)return Results.NotFound();
            if(!await db.Departments.AnyAsync(d=>d.Id==x.DepartmentId&&d.Active))return Results.BadRequest(new{error="Phòng ban không hợp lệ."});
            var user=x.UserId.HasValue?await db.Users.FindAsync(x.UserId.Value):await db.Users.SingleOrDefaultAsync(u=>u.Email==x.Email.Trim().ToLower());
            if(user is null||!user.Active||!user.Verified||user.Role=="ADMIN")return Results.BadRequest(new{error="Tài khoản nhân viên không hợp lệ."});
            if(await db.Employees.AnyAsync(other=>other.Id!=id&&other.UserId==user.Id))return Results.Conflict(new{error="Tài khoản đã được gắn với nhân viên khác."});
            e.EmployeeCode=x.EmployeeCode.Trim();e.FullName=x.FullName.Trim();e.Email=user.Email;e.Phone=x.Phone;e.DepartmentId=x.DepartmentId;e.Position=x.Position;e.HireDate=x.HireDate;e.UserId=user.Id;user.Role="EMPLOYEE";
            try{await db.SaveChangesAsync();}catch(DbUpdateException){return Results.Conflict(new{error="Mã nhân viên đã tồn tại."});}
            return Results.Ok(e);
        });
        emp.MapDelete("/{id:int}", async (int id, AppDb db) => { var e=await db.Employees.FindAsync(id);if(e is null)return Results.NotFound();e.Active=false;await db.SaveChangesAsync();return Results.NoContent(); });

        var shift=api.MapGroup("/shifts");
        shift.MapGet("/",async(AppDb db)=>await db.Shifts.Where(s=>s.Active).ToListAsync());
        shift.MapPost("/",async(ShiftDto x,AppDb db)=>{if(string.IsNullOrWhiteSpace(x.Name)||x.GracePeriodMinutes<0)return Results.BadRequest();var s=new Shift{Name=x.Name,StartTime=x.StartTime,EndTime=x.EndTime,GracePeriodMinutes=x.GracePeriodMinutes,Description=x.Description};db.Shifts.Add(s);await db.SaveChangesAsync();return Results.Created($"/api/v1/shifts/{s.Id}",s);}).RequireAuthorization("ADMIN");
        shift.MapPut("/{id:int}",async(int id,ShiftDto x,AppDb db)=>{var s=await db.Shifts.FindAsync(id);if(s is null)return Results.NotFound();s.Name=x.Name;s.StartTime=x.StartTime;s.EndTime=x.EndTime;s.GracePeriodMinutes=x.GracePeriodMinutes;s.Description=x.Description;await db.SaveChangesAsync();return Results.Ok(s);}).RequireAuthorization("ADMIN");
        shift.MapDelete("/{id:int}",async(int id,AppDb db)=>{var s=await db.Shifts.FindAsync(id);if(s is null)return Results.NotFound();s.Active=false;await db.SaveChangesAsync();return Results.NoContent();}).RequireAuthorization("ADMIN");
        var assignment=api.MapGroup("/shift-assignments");
        assignment.MapGet("/",async(int? employeeId,DateOnly? date,AppDb db,ClaimsPrincipal p)=>{var e=await OwnEmployee(db,p);if(!p.IsInRole("ADMIN")&&(e is null||employeeId.HasValue&&employeeId!=e.Id))return Results.Forbid();var q=db.ShiftAssignments.AsQueryable();if(!p.IsInRole("ADMIN"))q=q.Where(x=>x.EmployeeId==e!.Id);else if(employeeId.HasValue)q=q.Where(x=>x.EmployeeId==employeeId);if(date.HasValue)q=q.Where(x=>x.WorkDate==date);return Results.Ok(await q.OrderByDescending(x=>x.WorkDate).Take(100).ToListAsync());});
        assignment.MapPost("/",async(AssignmentDto x,AppDb db)=>{if(await db.ShiftAssignments.AnyAsync(a=>a.EmployeeId==x.EmployeeId&&a.WorkDate==x.WorkDate))return Results.Conflict();var a=new ShiftAssignment{EmployeeId=x.EmployeeId,ShiftId=x.ShiftId,WorkDate=x.WorkDate};db.ShiftAssignments.Add(a);await db.SaveChangesAsync();return Results.Created($"/api/v1/shift-assignments/{a.Id}",a);}).RequireAuthorization("ADMIN");
        assignment.MapDelete("/{id:int}",async(int id,AppDb db)=>{var a=await db.ShiftAssignments.FindAsync(id);if(a is null)return Results.NotFound();if(await db.AttendanceRecords.AnyAsync(r=>r.ShiftAssignmentId==id))return Results.Conflict();db.Remove(a);await db.SaveChangesAsync();return Results.NoContent();}).RequireAuthorization("ADMIN");
        var office=api.MapGroup("/offices");
        office.MapGet("/",async(AppDb db)=>await db.Offices.Where(o=>o.Active).ToListAsync());
        office.MapPost("/",async(OfficeDto x,AppDb db)=>{if(x.AllowedRadiusMeters<=0)return Results.BadRequest();var o=new Office{Name=x.Name,Latitude=x.Latitude,Longitude=x.Longitude,AllowedRadiusMeters=x.AllowedRadiusMeters};db.Offices.Add(o);await db.SaveChangesAsync();return Results.Created($"/api/v1/offices/{o.Id}",o);}).RequireAuthorization("ADMIN");
        office.MapPut("/{id:int}",async(int id,OfficeDto x,AppDb db)=>{var o=await db.Offices.FindAsync(id);if(o is null)return Results.NotFound();o.Name=x.Name;o.Latitude=x.Latitude;o.Longitude=x.Longitude;o.AllowedRadiusMeters=x.AllowedRadiusMeters;await db.SaveChangesAsync();return Results.Ok(o);}).RequireAuthorization("ADMIN");
        api.MapPost("/qr/generate",async(int officeId,AppDb db)=>
        {
            if(!await db.Offices.AnyAsync(o=>o.Id==officeId&&o.Active))return Results.NotFound();
            var token=Convert.ToBase64String(System.Security.Cryptography.RandomNumberGenerator.GetBytes(32));
            var expires=DateTime.UtcNow.AddMinutes(5);
            db.QrChallenges.Add(new QrChallenge{OfficeId=officeId,Hash=AuthService.Hash(token),ExpiresUtc=expires});
            await db.SaveChangesAsync();return Results.Ok(new{token,expiresUtc=expires});
        }).RequireAuthorization("ADMIN");
        MapAttendance(api);
        MapRequests(api);
    }

    private static void MapAttendance(RouteGroupBuilder api)
    {
        var g=api.MapGroup("/attendance");
        g.MapPost("/check-in",async(CheckDto x,ClaimsPrincipal p,AppDb db)=>
        {
            var e=await OwnEmployee(db,p);if(e is null)return Results.Forbid();
            var now=DateTime.UtcNow;var day=LocalDay(now);
            if(string.IsNullOrWhiteSpace(x.QrToken)||!FreshLocation(x,now))return Results.BadRequest(new{error="QR hoặc vị trí không hợp lệ, quá cũ hoặc thiếu chính xác."});
            var q=await db.QrChallenges.SingleOrDefaultAsync(v=>v.Hash==AuthService.Hash(x.QrToken));
            if(q is null||q.UsedUtc is not null||q.ExpiresUtc<=now)return Results.BadRequest(new{error="QR đã hết hạn hoặc đã được sử dụng."});
            var o=await db.Offices.FindAsync(q.OfficeId);
            if(o is null||!o.Active||Meters(x.Latitude,x.Longitude,o.Latitude,o.Longitude)>o.AllowedRadiusMeters)return Results.BadRequest(new{error="Ngoài phạm vi văn phòng."});
            var a=await db.ShiftAssignments.SingleOrDefaultAsync(v=>v.EmployeeId==e.Id&&v.WorkDate==day);
            if(a is null)
            {
                var defaultShift=await db.Shifts.Where(v=>v.Active).OrderBy(v=>v.Id).FirstOrDefaultAsync();
                if(defaultShift is null)return Results.BadRequest(new{error="Chưa có ca làm việc. Admin cần tạo ca trước."});
                a=new ShiftAssignment{EmployeeId=e.Id,ShiftId=defaultShift.Id,WorkDate=day};
                db.ShiftAssignments.Add(a);
                try { await db.SaveChangesAsync(); }
                catch (DbUpdateException) { return Results.Conflict(new{error="Ca hôm nay vừa được tạo; hãy thử lại."}); }
            }
            if(await db.AttendanceRecords.AnyAsync(r=>r.ShiftAssignmentId==a.Id))return Results.Conflict(new{error="Đã check-in."});
            var s=await db.Shifts.FindAsync(a.ShiftId);
            if(s is null||!s.Active)return Results.BadRequest(new{error="Ca làm việc không còn hiệu lực."});
            var record=new AttendanceRecord{EmployeeId=e.Id,ShiftAssignmentId=a.Id,CheckInUtc=now,Late=now>ShiftUtc(day,s.StartTime).AddMinutes(s.GracePeriodMinutes),OfficeId=o.Id};
            await using var tx=await db.Database.BeginTransactionAsync();
            var claimed=await db.QrChallenges.Where(v=>v.Id==q.Id&&v.UsedUtc==null&&v.ExpiresUtc>now).ExecuteUpdateAsync(set=>set.SetProperty(v=>v.UsedUtc,now));
            if(claimed!=1)return Results.Conflict(new{error="QR vừa được sử dụng."});
            db.AttendanceRecords.Add(record);
            try { await db.SaveChangesAsync(); }
            catch (DbUpdateException) { return Results.Conflict(new{error="Đã có chấm công cho ca hôm nay."}); }
            await tx.CommitAsync();
            return Results.Ok(record);
        }).RequireAuthorization("EMPLOYEE");
        g.MapPost("/check-out",async(CheckDto x,ClaimsPrincipal p,AppDb db)=>
        {
            var e=await OwnEmployee(db,p);if(e is null)return Results.Forbid();
            var r=await db.AttendanceRecords.Where(v=>v.EmployeeId==e.Id&&v.CheckOutUtc==null).OrderByDescending(v=>v.CheckInUtc).FirstOrDefaultAsync();
            if(r is null)return Results.BadRequest(new{error="Chưa check-in."});
            var now=DateTime.UtcNow;
            if(string.IsNullOrWhiteSpace(x.QrToken)||!FreshLocation(x,now))return Results.BadRequest(new{error="QR hoặc vị trí không hợp lệ, quá cũ hoặc thiếu chính xác."});
            var q=await db.QrChallenges.SingleOrDefaultAsync(v=>v.Hash==AuthService.Hash(x.QrToken));
            if(q is null||q.UsedUtc is not null||q.ExpiresUtc<=now||q.OfficeId!=r.OfficeId)return Results.BadRequest(new{error="QR không hợp lệ."});
            var o=await db.Offices.FindAsync(q.OfficeId);
            if(o is null||!o.Active||Meters(x.Latitude,x.Longitude,o.Latitude,o.Longitude)>o.AllowedRadiusMeters)return Results.BadRequest(new{error="Ngoài phạm vi văn phòng."});
            var a=await db.ShiftAssignments.FindAsync(r.ShiftAssignmentId);var s=await db.Shifts.FindAsync(a!.ShiftId);
            var endDate=s!.EndTime<=s.StartTime?a.WorkDate.AddDays(1):a.WorkDate;
            await using var tx=await db.Database.BeginTransactionAsync();
            var claimed=await db.QrChallenges.Where(v=>v.Id==q.Id&&v.UsedUtc==null&&v.ExpiresUtc>now).ExecuteUpdateAsync(set=>set.SetProperty(v=>v.UsedUtc,now));
            if(claimed!=1)return Results.Conflict(new{error="QR vừa được sử dụng."});
            r.CheckOutUtc=now;r.EarlyLeave=now<ShiftUtc(endDate,s.EndTime);await db.SaveChangesAsync();await tx.CommitAsync();return Results.Ok(r);
        }).RequireAuthorization("EMPLOYEE");
        g.MapGet("/today",async(ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);if(e is null)return Results.Forbid();var day=LocalDay(DateTime.UtcNow);var a=await db.ShiftAssignments.SingleOrDefaultAsync(v=>v.EmployeeId==e.Id&&v.WorkDate==day);return Results.Ok(new{assignment=a,record=a is null?null:await db.AttendanceRecords.SingleOrDefaultAsync(r=>r.ShiftAssignmentId==a.Id)});}).RequireAuthorization("EMPLOYEE");
        g.MapGet("/history",async(ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);return e is null?Results.Forbid():Results.Ok(await db.AttendanceRecords.Where(r=>r.EmployeeId==e.Id).OrderByDescending(r=>r.CheckInUtc).Take(100).ToListAsync());}).RequireAuthorization("EMPLOYEE");
        g.MapGet("/summary",async(ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);if(e is null)return Results.Forbid();var records=await db.AttendanceRecords.Where(r=>r.EmployeeId==e.Id).ToListAsync();return Results.Ok(new{days=records.Count,late=records.Count(r=>r.Late),earlyLeave=records.Count(r=>r.EarlyLeave),hours=records.Sum(r=>r.CheckOutUtc.HasValue?(r.CheckOutUtc.Value-r.CheckInUtc).TotalHours:0)});}).RequireAuthorization("EMPLOYEE");
        g.MapGet("/admin-summary",async(AppDb db)=>
        {
            var day=LocalDay(DateTime.UtcNow);
            var assignmentIds=await db.ShiftAssignments.Where(a=>a.WorkDate==day).Select(a=>a.Id).ToListAsync();
            var todayRecords=await db.AttendanceRecords.Where(r=>assignmentIds.Contains(r.ShiftAssignmentId)).ToListAsync();
            var recent=await db.AuditLogs.OrderByDescending(a=>a.AtUtc).Take(5).Select(a=>new{a.Action,a.Subject,a.AtUtc}).ToListAsync();
            return Results.Ok(new{
                totalEmployees=await db.Employees.CountAsync(e=>e.Active),
                workingToday=todayRecords.Count(r=>r.CheckOutUtc==null),
                lateToday=todayRecords.Count(r=>r.Late),
                onLeaveToday=await db.LeaveRequests.CountAsync(r=>r.Status=="APPROVED"&&r.StartDate<=day&&r.EndDate>=day),
                pendingRequests=await db.LeaveRequests.CountAsync(r=>r.Status=="PENDING")+await db.CorrectionRequests.CountAsync(r=>r.Status=="PENDING"),
                checkedInToday=todayRecords.Count,
                recent
            });
        }).RequireAuthorization("ADMIN");
        g.MapGet("/reports",async(AppDb db)=>await db.AttendanceRecords.OrderByDescending(r=>r.CheckInUtc).Take(200).ToListAsync()).RequireAuthorization("ADMIN");
        g.MapGet("/employee/{id:int}",async(int id,AppDb db)=>await db.AttendanceRecords.Where(r=>r.EmployeeId==id).OrderByDescending(r=>r.CheckInUtc).Take(100).ToListAsync()).RequireAuthorization("ADMIN");
    }

    private static void MapRequests(RouteGroupBuilder api)
    {
        var leave=api.MapGroup("/leave-requests");
        leave.MapGet("/",async(ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);return Results.Ok(await db.LeaveRequests.Where(r=>p.IsInRole("ADMIN")||e!=null&&r.EmployeeId==e.Id).OrderByDescending(r=>r.Id).ToListAsync());});
        leave.MapPost("/",async(LeaveDto x,ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);if(e is null)return Results.Forbid();if(x.EndDate<x.StartDate||string.IsNullOrWhiteSpace(x.Reason))return Results.BadRequest();var r=new LeaveRequest{EmployeeId=e.Id,StartDate=x.StartDate,EndDate=x.EndDate,Reason=x.Reason};db.LeaveRequests.Add(r);await db.SaveChangesAsync();return Results.Created($"/api/v1/leave-requests/{r.Id}",r);}).RequireAuthorization("EMPLOYEE");
        leave.MapPut("/{id:int}",async(int id,LeaveDto x,ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);var r=await db.LeaveRequests.FindAsync(id);if(e is null||r is null||r.EmployeeId!=e.Id||r.Status!="PENDING")return Results.Forbid();r.StartDate=x.StartDate;r.EndDate=x.EndDate;r.Reason=x.Reason;await db.SaveChangesAsync();return Results.Ok(r);}).RequireAuthorization("EMPLOYEE");
        leave.MapDelete("/{id:int}",async(int id,ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);var r=await db.LeaveRequests.FindAsync(id);if(e is null||r is null||r.EmployeeId!=e.Id||r.Status!="PENDING")return Results.Forbid();db.Remove(r);await db.SaveChangesAsync();return Results.NoContent();}).RequireAuthorization("EMPLOYEE");
        leave.MapPost("/{id:int}/approve",async(int id,ClaimsPrincipal p,AppDb db)=>await ReviewLeave(id,"APPROVED",null,p,db)).RequireAuthorization("ADMIN");
        leave.MapPost("/{id:int}/reject",async(int id,ReviewDto x,ClaimsPrincipal p,AppDb db)=>await ReviewLeave(id,"REJECTED",x.Reason,p,db)).RequireAuthorization("ADMIN");
        var correction=api.MapGroup("/correction-requests");
        correction.MapGet("/",async(ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);return Results.Ok(await db.CorrectionRequests.Where(r=>p.IsInRole("ADMIN")||e!=null&&r.EmployeeId==e.Id).OrderByDescending(r=>r.Id).ToListAsync());});
        correction.MapPost("/",async(CorrectionDto x,ClaimsPrincipal p,AppDb db)=>{var e=await OwnEmployee(db,p);var a=await db.AttendanceRecords.FindAsync(x.AttendanceRecordId);if(e is null||a is null||a.EmployeeId!=e.Id||a.CheckOutUtc is not null||x.ProposedCheckOutUtc<=a.CheckInUtc||string.IsNullOrWhiteSpace(x.Reason))return Results.BadRequest();var r=new CorrectionRequest{AttendanceRecordId=a.Id,EmployeeId=e.Id,ProposedCheckOutUtc=x.ProposedCheckOutUtc,Reason=x.Reason};db.CorrectionRequests.Add(r);await db.SaveChangesAsync();return Results.Created($"/api/v1/correction-requests/{r.Id}",r);}).RequireAuthorization("EMPLOYEE");
        correction.MapPost("/{id:int}/approve",async(int id,ClaimsPrincipal p,AppDb db)=>await ReviewCorrection(id,"APPROVED",null,p,db)).RequireAuthorization("ADMIN");
        correction.MapPost("/{id:int}/reject",async(int id,ReviewDto x,ClaimsPrincipal p,AppDb db)=>await ReviewCorrection(id,"REJECTED",x.Reason,p,db)).RequireAuthorization("ADMIN");
    }
    private static async Task<IResult> ReviewLeave(int id,string status,string? reason,ClaimsPrincipal p,AppDb db)
    {
        var r=await db.LeaveRequests.FindAsync(id);if(r is null)return Results.NotFound();if(r.Status!="PENDING")return Results.Conflict();if(status=="REJECTED"&&string.IsNullOrWhiteSpace(reason))return Results.BadRequest();r.Status=status;r.ReviewReason=reason;db.AuditLogs.Add(new AuditLog{ActorUserId=Id(p),Action="LEAVE_"+status,Subject=id.ToString(),AtUtc=DateTime.UtcNow});await db.SaveChangesAsync();return Results.Ok(r);
    }
    private static async Task<IResult> ReviewCorrection(int id,string status,string? reason,ClaimsPrincipal p,AppDb db)
    {
        var r=await db.CorrectionRequests.FindAsync(id);if(r is null)return Results.NotFound();if(r.Status!="PENDING")return Results.Conflict();if(status=="REJECTED"&&string.IsNullOrWhiteSpace(reason))return Results.BadRequest();r.Status=status;r.ReviewReason=reason;if(status=="APPROVED"){var a=await db.AttendanceRecords.FindAsync(r.AttendanceRecordId);if(a is null||a.CheckOutUtc is not null)return Results.Conflict();a.CheckOutUtc=r.ProposedCheckOutUtc;}db.AuditLogs.Add(new AuditLog{ActorUserId=Id(p),Action="CORRECTION_"+status,Subject=id.ToString(),AtUtc=DateTime.UtcNow});await db.SaveChangesAsync();return Results.Ok(r);
    }
}
