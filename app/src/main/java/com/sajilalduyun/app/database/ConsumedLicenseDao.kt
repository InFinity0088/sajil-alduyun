package com.sajilalduyun.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sajilalduyun.app.model.ConsumedLicense

@Dao
interface ConsumedLicenseDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(license: ConsumedLicense)

    @Query("SELECT * FROM consumed_licenses WHERE codeHash = :codeHash LIMIT 1")
    suspend fun getByCodeHash(codeHash: String): ConsumedLicense?

    @Query("SELECT * FROM consumed_licenses")
    suspend fun getAll(): List<ConsumedLicense>
}
