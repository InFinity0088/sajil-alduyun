package com.sajilalduyun.app.database



import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.model.PasswordResetCode


// This tells Room: "our database has 2 tables: CustomerDebt + DebtHistory, and User + PasswordResetCode"
@Database(entities = [CustomerDebt::class, DebtHistory::class, User::class, PasswordResetCode::class], version = 3)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    // The door to our debt table
    abstract fun debtDao(): DebtDao
    abstract fun userDao(): UserDao
    abstract fun debtHistoryDao(): DebtHistoryDao
    abstract fun passwordResetCodeDao(): PasswordResetCodeDao

    companion object {
        // Only one instance of the database ever exists
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sajil_alduyun_db" // The name of the database file on the phone
                ).fallbackToDestructiveMigration(false)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}