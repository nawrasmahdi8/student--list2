package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "students")
data class Student(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val registrationNumber: String,
    val pageNumber: String,
    val schoolId: String,
    val isSynced: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
