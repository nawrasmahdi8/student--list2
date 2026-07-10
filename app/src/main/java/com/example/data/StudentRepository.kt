package com.example.data

import kotlinx.coroutines.flow.Flow

class StudentRepository(private val studentDao: StudentDao) {
    val allStudentsFlow: Flow<List<StudentEntity>> = studentDao.getAllStudentsFlow()

    suspend fun getAllStudents(): List<StudentEntity> {
        return studentDao.getAllStudents()
    }

    suspend fun getStudentByRegistryAndPage(registrationNumber: String, pageNumber: String): StudentEntity? {
        return studentDao.getStudentByRegistryAndPage(registrationNumber, pageNumber)
    }

    suspend fun getStudentByNormalizedName(normalizedName: String): StudentEntity? {
        return studentDao.getStudentByNormalizedName(normalizedName)
    }

    suspend fun getLastStudentInRegistry(registrationId: String): StudentEntity? {
        return studentDao.getLastStudentInRegistry(registrationId)
    }

    suspend fun getLastStudentOverall(): StudentEntity? {
        return studentDao.getLastStudentOverall()
    }

    suspend fun insertStudent(student: StudentEntity): Long {
        return studentDao.insertStudent(student)
    }

    suspend fun updateStudent(student: StudentEntity) {
        studentDao.updateStudent(student)
    }

    suspend fun deleteStudent(student: StudentEntity) {
        studentDao.deleteStudent(student)
    }

    suspend fun deleteAllStudents() {
        studentDao.deleteAllStudents()
    }

    suspend fun updateStudentStatus(id: Int, status: String) {
        studentDao.updateStudentStatus(id, status)
    }

    suspend fun insertWhitelistedPair(pair: WhitelistedPair) {
        studentDao.insertWhitelistedPair(pair)
    }
}
