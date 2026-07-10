package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "config_table")
data class AppConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    @ColumnInfo(name = "current_role")
    val currentRole: String = "GUEST",
    @ColumnInfo(name = "school_id")
    val schoolId: String? = null,
    @ColumnInfo(name = "school_name")
    val schoolName: String? = null,
    @ColumnInfo(name = "manager_name")
    val managerName: String? = null
)
