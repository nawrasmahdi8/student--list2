package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface StudentDao {
    @Query("SELECT * FROM students_table ORDER BY id DESC")
    fun getAllStudentsFlow(): Flow<List<StudentEntity>>

    @Query("SELECT * FROM students_table")
    suspend fun getAllStudents(): List<StudentEntity>

    @Query("SELECT * FROM students_table WHERE registryNumber = :registryNumber AND page_number = :pageNumber LIMIT 1")
    suspend fun getStudentByRegistryAndPage(registryNumber: String, pageNumber: String): StudentEntity?

    @Query("SELECT * FROM students_table WHERE student_name = :studentName LIMIT 1")
    suspend fun getStudentByNormalizedName(studentName: String): StudentEntity?

    @Query("SELECT * FROM students_table WHERE registryNumber = :registryNumber ORDER BY id DESC LIMIT 1")
    suspend fun getLastStudentInRegistry(registryNumber: String): StudentEntity?

    @Query("SELECT * FROM students_table ORDER BY page_number DESC LIMIT 1")
    suspend fun getLastStudentOverall(): StudentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudent(student: StudentEntity): Long

    @Update
    suspend fun updateStudent(student: StudentEntity)
    
    @Query("UPDATE students_table SET status = :status WHERE id = :id")
    suspend fun updateStudentStatus(id: Int, status: String)

    @Delete
    suspend fun deleteStudent(student: StudentEntity)

    @Query("DELETE FROM students_table")
    suspend fun deleteAllStudents()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWhitelistedPair(pair: WhitelistedPair)

    @Query("SELECT * FROM whitelisted_pairs WHERE (studentId1 = :id1 AND studentId2 = :id2) OR (studentId1 = :id2 AND studentId2 = :id1)")
    suspend fun getWhitelistedPair(id1: Int, id2: Int): WhitelistedPair?

    @Query("UPDATE students_table SET school_id = :newSchoolId WHERE school_id IS NULL")
    suspend fun bulkUpdateSchoolId(newSchoolId: String)

    @Query("SELECT * FROM students_table WHERE isSynced = 0")
    suspend fun getUnsyncedStudents(): List<StudentEntity>

    @Query("UPDATE students_table SET isSynced = 1 WHERE id = :studentId")
    suspend fun markAsSynced(studentId: Int)

    @Query("SELECT * FROM students_table WHERE school_id = :schoolId")
    suspend fun getAllBySchoolId(schoolId: String): List<StudentEntity>
}