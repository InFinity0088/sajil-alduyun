package com.sajilalduyun.app.database

import androidx.room.*
import com.sajilalduyun.app.model.User

@Dao
interface UserDao {

    // Save a new user
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    // Get user by their ID
    @Query("SELECT * FROM users WHERE id = :userId")
    suspend fun getUserById(userId: String): User?

    // Get all workers (for owner to manage)
    @Query("SELECT * FROM users WHERE role = 'WORKER'")
    suspend fun getAllWorkers(): List<User>

    // Get the owner account
    @Query("SELECT * FROM users WHERE role = 'OWNER' LIMIT 1")
    suspend fun getOwner(): User?

    // Update user (example: deactivate a worker)
    @Update
    suspend fun updateUser(user: User)

    // Get multiple users at once (fixes N+1 queries)
    @Query("SELECT * FROM users WHERE id IN (:userIds)")
    suspend fun getUsersByIds(userIds: List<String>): List<User>

    // Get user by phone number
    @Query("SELECT * FROM users WHERE phoneNumber = :phoneNumber")
    suspend fun getUserByPhone(phoneNumber: String): User?

    // Delete a user
    @Delete
    suspend fun deleteUser(user: User)
}