package com.example

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore by preferencesDataStore(name = "registration_prefs")

class DataStoreManager(private val context: Context) {
    companion object {
        val SCHOOL_NAME = stringPreferencesKey("school_name")
        val PROVINCE = stringPreferencesKey("province")
        val MANAGER_NAME = stringPreferencesKey("manager_name")
        val PHONE_NUMBER = stringPreferencesKey("phone_number")
    }

    suspend fun saveRegistrationData(
        schoolName: String?,
        province: String?,
        managerName: String?,
        phoneNumber: String?
    ) {
        context.dataStore.edit { prefs ->
            prefs[SCHOOL_NAME] = schoolName ?: ""
            prefs[PROVINCE] = province ?: ""
            prefs[MANAGER_NAME] = managerName ?: ""
            prefs[PHONE_NUMBER] = phoneNumber ?: ""
        }
    }

    val registrationData: Flow<Map<String, String>> = context.dataStore.data.map { prefs ->
        mapOf(
            "schoolName" to (prefs[SCHOOL_NAME] ?: ""),
            "province" to (prefs[PROVINCE] ?: ""),
            "managerName" to (prefs[MANAGER_NAME] ?: ""),
            "phoneNumber" to (prefs[PHONE_NUMBER] ?: "")
        )
    }
}
