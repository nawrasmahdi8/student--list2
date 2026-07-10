package com.example.data

data class Conflict(
    val incomingStudent: StudentEntity,
    val existingStudent: StudentEntity
)

enum class ResolutionAction {
    OVERWRITE_LOCAL,
    KEEP_LOCAL,
    CREATE_DUPLICATE
}
