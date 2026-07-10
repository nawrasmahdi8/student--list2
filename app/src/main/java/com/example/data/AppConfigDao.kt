package com.example.data

import androidx.room.*

@Dao
interface AppConfigDao {
    @Query("SELECT * FROM config_table WHERE id = 1 LIMIT 1")
    suspend fun getConfig(): AppConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfig(config: AppConfigEntity)
}
