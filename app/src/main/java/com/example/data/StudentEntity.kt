package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "students_table")
data class StudentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    @ColumnInfo(name = "student_name")
    val name: String,
    val normalizedName: String,
    @ColumnInfo(name = "registryNumber")
    val registrationId: String,
    @ColumnInfo(name = "page_number")
    val pageNumber: Int,
    val photoPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "Active", // Active, Duplicate_Pending, Duplicate_Cancelled
    @ColumnInfo(name = "school_id")
    val schoolId: String? = null,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    fun getPublicName(isGuestMode: Boolean): String = name
    fun getPublicRegistrationDetails(isGuestMode: Boolean): String = "قيد رقم: $registrationId"
}

@Entity(tableName = "whitelisted_pairs")
data class WhitelistedPair(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val studentId1: Int,
    val studentId2: Int
)
