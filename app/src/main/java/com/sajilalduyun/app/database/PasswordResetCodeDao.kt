package com.sajilalduyun.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.sajilalduyun.app.model.PasswordResetCode

@Dao
interface PasswordResetCodeDao {

    @Insert
    suspend fun insert(code: PasswordResetCode)

    @Query("SELECT * FROM password_reset_codes WHERE phoneNumber = :phoneNumber AND isUsed = 0 ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestCodeForPhone(phoneNumber: String): PasswordResetCode?

    @Query("SELECT * FROM password_reset_codes WHERE userId = :userId AND code = :code AND isUsed = 0")
    suspend fun getCodeByUserAndCode(userId: String, code: String): PasswordResetCode?

    @Update
    suspend fun update(code: PasswordResetCode)

    @Query("DELETE FROM password_reset_codes WHERE expiresAt < :now")
    suspend fun deleteExpiredCodes(now: Long = System.currentTimeMillis())
}
