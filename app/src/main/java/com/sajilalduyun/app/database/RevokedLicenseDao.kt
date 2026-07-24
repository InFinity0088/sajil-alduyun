package com.sajilalduyun.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sajilalduyun.app.model.RevokedLicense

@Dao
interface RevokedLicenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(revoked: RevokedLicense)

    @Query("SELECT * FROM revoked_licenses WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getByDeviceId(deviceId: String): RevokedLicense?
}
