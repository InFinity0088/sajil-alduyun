package com.sajilalduyun.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sajilalduyun.app.model.DebtHistory

@Dao
interface DebtHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: DebtHistory)

    @Query("SELECT * FROM debt_history WHERE debtId = :debtId ORDER BY changedAt DESC")
    suspend fun getHistoryForDebt(debtId: String): List<DebtHistory>
}
