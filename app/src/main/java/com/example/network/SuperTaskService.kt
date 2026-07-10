package com.example.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Header
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class OtpRequest(@com.squareup.moshi.Json(name = "phone") val phone: String)

@JsonClass(generateAdapter = true)
data class PhoneRequest(
    val phone: String
)

@JsonClass(generateAdapter = true)
data class StudentDto(
    val studentName: String,
    val registrationNumber: String,
    val pageNumber: String,
    val schoolId: String
)

@JsonClass(generateAdapter = true)
data class SchoolDto(
    val school_name: String,
    val governorate: String
)

@JsonClass(generateAdapter = true)
data class ProfileDto(
    val school_id: String,
    val full_name: String,
    val phone: String,
    val role: String
)

@JsonClass(generateAdapter = true)
data class SchoolResponse(
    val id: String
)

interface SuperTaskService {
    @POST("functions/v1/super-task")
    suspend fun sendPhoneNumber(
        @Body request: OtpRequest,
        @Header("Content-Type") contentType: String = "application/json"
    ): retrofit2.Response<Unit>

    @POST("rest/v1/schools")
    suspend fun createSchool(
        @Body request: SchoolDto,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Prefer") prefer: String = "return=representation",
        @Header("Authorization") auth: String
    ): retrofit2.Response<List<SchoolResponse>>

    @POST("rest/v1/profiles")
    suspend fun createProfile(
        @Body request: ProfileDto,
        @Header("Content-Type") contentType: String = "application/json",
        @Header("Authorization") auth: String
    ): retrofit2.Response<Unit>

    @POST("functions/v1/sync-students")
    suspend fun syncStudents(
        @Body request: List<StudentDto>,
        @Header("Content-Type") contentType: String = "application/json"
    ): retrofit2.Response<Unit>
}
