package com.sajilalduyun.app.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sajilalduyun.app.model.PaymentPlan

@Dao
interface PlanDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(plan: PaymentPlan)

    @Update
    suspend fun update(plan: PaymentPlan)

    @Query("DELETE FROM plans WHERE id = :planId")
    suspend fun deleteById(planId: Long)

    @Query("SELECT * FROM plans ORDER BY id ASC")
    suspend fun getAllPlans(): List<PaymentPlan>

    @Query("SELECT * FROM plans WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getActivePlans(): List<PaymentPlan>

    @Query("SELECT * FROM plans WHERE id = :planId LIMIT 1")
    suspend fun getPlanById(planId: Long): PaymentPlan?

    @Query("SELECT COUNT(*) FROM plans")
    suspend fun count(): Int
}
