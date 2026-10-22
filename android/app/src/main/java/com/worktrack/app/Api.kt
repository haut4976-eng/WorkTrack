package com.worktrack.app

import com.google.gson.JsonObject
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Route
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.DELETE
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

data class LoginRequest(val username: String, val password: String)
data class ForgotPasswordRequest(val username: String)
data class OtpRequest(val username: String, val code: String)
data class ResetPasswordRequest(val username: String, val code: String, val newPassword: String)
data class RegisterRequest(val fullName: String, val username: String, val email: String, val phone: String?, val password: String, val confirmPassword: String)
data class Session(val accessToken: String, val refreshToken: String, val role: String, val fullName: String)
data class RefreshRequest(val refreshToken: String)
data class Profile(val id: Int, val username: String, val fullName: String, val email: String, val phone: String?, val role: String)
data class ProfileRequest(val fullName: String, val email: String, val phone: String?)
data class ChangePasswordRequest(val currentPassword: String, val newPassword: String)
data class AppUser(val id: Int, val username: String, val fullName: String, val email: String, val role: String, val active: Boolean, val verified: Boolean)
data class UpdateUserRequest(val role: String, val active: Boolean)
data class Department(val id: Int, val code: String, val name: String, val description: String = "")
data class DepartmentRequest(val code: String, val name: String, val description: String = "")
data class Employee(val id: Int, val employeeCode: String, val fullName: String, val email: String, val position: String, val active: Boolean, val phone: String? = null, val departmentId: Int = 0, val hireDate: String = "")
data class EmployeeRequest(val employeeCode: String, val fullName: String, val email: String, val phone: String?, val departmentId: Int, val position: String, val hireDate: String, val userId: Int? = null)
data class Shift(val id: Int, val name: String, val startTime: String, val endTime: String, val gracePeriodMinutes: Int, val description: String = "")
data class ShiftRequest(val name: String, val startTime: String, val endTime: String, val gracePeriodMinutes: Int, val description: String = "")
data class Assignment(val id: Int, val employeeId: Int, val shiftId: Int, val workDate: String)
data class AssignmentRequest(val employeeId: Int, val shiftId: Int, val workDate: String)
data class LeaveRequest(val id: Int, val employeeId: Int, val startDate: String, val endDate: String, val reason: String, val status: String)
data class LeaveInput(val startDate: String, val endDate: String, val reason: String)
data class CorrectionRequest(val id: Int, val employeeId: Int, val attendanceRecordId: Int, val proposedCheckOutUtc: String, val reason: String, val status: String)
data class CorrectionInput(val attendanceRecordId: Int, val proposedCheckOutUtc: String, val reason: String)
data class ReviewInput(val reason: String)
data class Attendance(val id: Int, val checkInUtc: String, val checkOutUtc: String?, val late: Boolean, val earlyLeave: Boolean, val employeeId: Int = 0, val officeId: Int = 0)
data class Summary(val days: Int, val late: Int, val earlyLeave: Int, val hours: Double)
data class AuditItem(val action: String, val subject: String, val atUtc: String)
data class AdminSummary(val totalEmployees: Int, val workingToday: Int, val lateToday: Int, val onLeaveToday: Int, val pendingRequests: Int, val checkedInToday: Int, val recent: List<AuditItem>)
data class Today(val assignment: JsonObject?, val record: Attendance?)
data class Office(val id: Int, val name: String, val latitude: Double, val longitude: Double, val allowedRadiusMeters: Double)
data class QrToken(val token: String, val expiresUtc: String)
data class CheckRequest(val qrToken: String, val latitude: Double, val longitude: Double, val accuracyMeters: Double, val locationTimestampUtc: String)

interface WorkTrackApi {
    @GET("api/v1/health") suspend fun health(): Response<JsonObject>
    @GET("api/v1/dev/otp") suspend fun demoOtp(@Query("username") username: String, @Query("purpose") purpose: String): Response<JsonObject>
    @POST("api/v1/auth/login") suspend fun login(@Body request: LoginRequest): Response<JsonObject>
    @POST("api/v1/auth/register") suspend fun register(@Body request: RegisterRequest): Response<JsonObject>
    @POST("api/v1/auth/forgot-password") suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<JsonObject>
    @POST("api/v1/auth/reset-password") suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<JsonObject>
    @POST("api/v1/auth/verify-registration") suspend fun verifyRegistration(@Body request: OtpRequest): Response<JsonObject>
    @POST("api/v1/auth/verify-login-otp") suspend fun otp(@Body request: OtpRequest): Response<Session>
    @POST("api/v1/auth/refresh") suspend fun refresh(@Body request: RefreshRequest): Response<Session>
    @POST("api/v1/auth/logout") suspend fun logout(@Body request: RefreshRequest): Response<Unit>
    @GET("api/v1/auth/me") suspend fun me(): Response<Profile>
    @PUT("api/v1/auth/me") suspend fun updateProfile(@Body request: ProfileRequest): Response<Profile>
    @POST("api/v1/auth/change-password") suspend fun changePassword(@Body request: ChangePasswordRequest): Response<JsonObject>
    @GET("api/v1/users") suspend fun users(): Response<List<AppUser>>
    @PUT("api/v1/users/{id}") suspend fun updateUser(@Path("id") id: Int, @Body request: UpdateUserRequest): Response<AppUser>
    @GET("api/v1/departments") suspend fun departments(): Response<List<Department>>
    @POST("api/v1/departments") suspend fun createDepartment(@Body request: DepartmentRequest): Response<Department>
    @PUT("api/v1/departments/{id}") suspend fun updateDepartment(@Path("id") id: Int, @Body request: DepartmentRequest): Response<Department>
    @DELETE("api/v1/departments/{id}") suspend fun deleteDepartment(@Path("id") id: Int): Response<Unit>
    @GET("api/v1/employees") suspend fun employees(): Response<List<Employee>>
    @POST("api/v1/employees") suspend fun createEmployee(@Body request: EmployeeRequest): Response<Employee>
    @PUT("api/v1/employees/{id}") suspend fun updateEmployee(@Path("id") id: Int, @Body request: EmployeeRequest): Response<Employee>
    @DELETE("api/v1/employees/{id}") suspend fun deleteEmployee(@Path("id") id: Int): Response<Unit>
    @GET("api/v1/attendance/today") suspend fun today(): Response<Today>
    @GET("api/v1/attendance/history") suspend fun history(): Response<List<Attendance>>
    @GET("api/v1/attendance/summary") suspend fun summary(): Response<Summary>
    @GET("api/v1/attendance/admin-summary") suspend fun adminSummary(): Response<AdminSummary>
    @GET("api/v1/attendance/reports") suspend fun reports(): Response<List<Attendance>>
    @GET("api/v1/shift-assignments") suspend fun assignments(): Response<List<Assignment>>
    @POST("api/v1/shift-assignments") suspend fun createAssignment(@Body request: AssignmentRequest): Response<Assignment>
    @DELETE("api/v1/shift-assignments/{id}") suspend fun deleteAssignment(@Path("id") id: Int): Response<Unit>
    @GET("api/v1/shifts") suspend fun shifts(): Response<List<Shift>>
    @POST("api/v1/shifts") suspend fun createShift(@Body request: ShiftRequest): Response<Shift>
    @PUT("api/v1/shifts/{id}") suspend fun updateShift(@Path("id") id: Int, @Body request: ShiftRequest): Response<Shift>
    @DELETE("api/v1/shifts/{id}") suspend fun deleteShift(@Path("id") id: Int): Response<Unit>
    @GET("api/v1/offices") suspend fun offices(): Response<List<Office>>
    @POST("api/v1/offices") suspend fun createOffice(@Body request: Office): Response<Office>
    @PUT("api/v1/offices/{id}") suspend fun updateOffice(@Path("id") id: Int, @Body request: Office): Response<Office>
    @GET("api/v1/leave-requests") suspend fun leaves(): Response<List<LeaveRequest>>
    @POST("api/v1/leave-requests") suspend fun createLeave(@Body request: LeaveInput): Response<LeaveRequest>
    @PUT("api/v1/leave-requests/{id}") suspend fun updateLeave(@Path("id") id: Int, @Body request: LeaveInput): Response<LeaveRequest>
    @DELETE("api/v1/leave-requests/{id}") suspend fun deleteLeave(@Path("id") id: Int): Response<Unit>
    @POST("api/v1/leave-requests/{id}/approve") suspend fun approveLeave(@Path("id") id: Int): Response<LeaveRequest>
    @POST("api/v1/leave-requests/{id}/reject") suspend fun rejectLeave(@Path("id") id: Int, @Body request: ReviewInput): Response<LeaveRequest>
    @GET("api/v1/correction-requests") suspend fun corrections(): Response<List<CorrectionRequest>>
    @POST("api/v1/correction-requests") suspend fun createCorrection(@Body request: CorrectionInput): Response<CorrectionRequest>
    @POST("api/v1/correction-requests/{id}/approve") suspend fun approveCorrection(@Path("id") id: Int): Response<CorrectionRequest>
    @POST("api/v1/correction-requests/{id}/reject") suspend fun rejectCorrection(@Path("id") id: Int, @Body request: ReviewInput): Response<CorrectionRequest>
    @POST("api/v1/qr/generate") suspend fun generateQr(@Query("officeId") officeId: Int): Response<QrToken>
    @POST("api/v1/attendance/check-in") suspend fun checkIn(@Body request: CheckRequest): Response<Attendance>
    @POST("api/v1/attendance/check-out") suspend fun checkOut(@Body request: CheckRequest): Response<Attendance>
}

class ApiFactory(private val token: () -> String?, private val store: SecureSessionStore? = null) {
    private val gson = Gson()
    private val refreshClient = OkHttpClient()
    val api: WorkTrackApi = Retrofit.Builder().baseUrl(BuildConfig.BASE_URL)
        .client(OkHttpClient.Builder().addInterceptor { chain ->
            val builder = chain.request().newBuilder()
            if (chain.request().header("Authorization") == null) token()?.let { builder.header("Authorization", "Bearer $it") }
            chain.proceed(builder.build())
        }.authenticator(object : Authenticator {
            override fun authenticate(route: Route?, response: okhttp3.Response): Request? {
                val secure = store ?: return null
                if (response.request.url.encodedPath.endsWith("/auth/refresh") || response.priorResponse != null) return null
                synchronized(secure) {
                    val current = secure.load() ?: return null
                    val sent = response.request.header("Authorization")
                    if (sent != "Bearer ${current.accessToken}")
                        return response.request.newBuilder().header("Authorization", "Bearer ${current.accessToken}").build()
                    val body = gson.toJson(RefreshRequest(current.refreshToken)).toRequestBody("application/json".toMediaType())
                    val request = Request.Builder().url(BuildConfig.BASE_URL + "api/v1/auth/refresh").post(body).build()
                    return try {
                        refreshClient.newCall(request).execute().use { refreshed ->
                            if (!refreshed.isSuccessful) return null
                            val next = gson.fromJson(refreshed.body?.charStream(), Session::class.java) ?: return null
                            secure.save(next)
                            response.request.newBuilder().header("Authorization", "Bearer ${next.accessToken}").build()
                        }
                    } catch (_: Exception) { null }
                }
            }
        }).build())
        .addConverterFactory(GsonConverterFactory.create()).build().create(WorkTrackApi::class.java)
}
