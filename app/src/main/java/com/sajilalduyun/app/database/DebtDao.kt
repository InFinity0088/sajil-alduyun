package com.sajilalduyun.app.database


import androidx.room.*
import com.sajilalduyun.app.model.CustomerDebt

// DAO = Data Access Object
// This is how we talk to the database.
// Each function is one action we can do.
@Dao
interface DebtDao {

    // Save a new debt (or replace if same ID exists)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDebt(debt: CustomerDebt)

    // Get ALL debts from the database
    @Query("SELECT * FROM debts ORDER BY createdAt DESC")
    suspend fun getAllDebts(): List<CustomerDebt>

    // Get only PENDING debts (for the pending requests screen)
    @Query("SELECT * FROM debts WHERE status = 'PENDING'")
    suspend fun getPendingDebts(): List<CustomerDebt>

    // Get one specific debt by its ID
    @Query("SELECT * FROM debts WHERE id = :debtId")
    suspend fun getDebtById(debtId: String): CustomerDebt?

    // Search debts by customer name (partial match)
    @Query("SELECT * FROM debts WHERE customerName LIKE '%' || :name || '%' ORDER BY createdAt DESC")
    suspend fun searchDebtsByName(name: String): List<CustomerDebt>

    // Update an existing debt
    @Update
    suspend fun updateDebt(debt: CustomerDebt)

    // Delete a debt permanently
    @Delete
    suspend fun deleteDebt(debt: CustomerDebt)
}