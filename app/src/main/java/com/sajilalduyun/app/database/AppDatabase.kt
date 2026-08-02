package com.sajilalduyun.app.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sajilalduyun.app.model.ConsumedLicense
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.PasswordResetCode
import com.sajilalduyun.app.model.PaymentPlan
import com.sajilalduyun.app.model.RevokedLicense
import com.sajilalduyun.app.model.User

@Database(
    entities = [
        CustomerDebt::class,
        DebtHistory::class,
        User::class,
        PasswordResetCode::class,
        ConsumedLicense::class,
        PaymentPlan::class,
        RevokedLicense::class
    ],
    version = 7
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun debtDao(): DebtDao
    abstract fun userDao(): UserDao
    abstract fun debtHistoryDao(): DebtHistoryDao
    abstract fun passwordResetCodeDao(): PasswordResetCodeDao
    abstract fun consumedLicenseDao(): ConsumedLicenseDao
    abstract fun planDao(): PlanDao
    abstract fun revokedLicenseDao(): RevokedLicenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        // Migration from v3 → v4: add consumed_licenses table
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `consumed_licenses` (
                        `codeHash` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `consumedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`codeHash`)
                    )
                """.trimIndent())
            }
        }

        // Migration from v4 → v5: add plans table, add planId + planDurationDays to debts
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `plans` (
                        `id` INTEGER NOT NULL,
                        `name` TEXT NOT NULL,
                        `durationDays` INTEGER NOT NULL,
                        `maxAmount` REAL NOT NULL,
                        `isActive` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                // Seed default plans
                db.execSQL("INSERT OR IGNORE INTO plans (id, name, durationDays, maxAmount, isActive) VALUES (1, '30 يوم', 30, 100000.0, 1)")
                db.execSQL("INSERT OR IGNORE INTO plans (id, name, durationDays, maxAmount, isActive) VALUES (2, 'مفتوح', 0, 25000.0, 1)")
                // Add new columns to debts table
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `planId` INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE `debts` ADD COLUMN `planDurationDays` INTEGER NOT NULL DEFAULT 0")
                // Migrate existing debts: map old planType to new planId + durationDays
                db.execSQL("UPDATE `debts` SET `planId` = 2, `planDurationDays` = 0 WHERE `planType` = 'UNLIMITED'")
                db.execSQL("UPDATE `debts` SET `planId` = 1, `planDurationDays` = 30 WHERE `planType` = 'THIRTY_DAY'")
            }
        }

        // Migration from v5 → v6: add revoked_licenses table for license transfers
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `revoked_licenses` (
                        `deviceId` TEXT NOT NULL,
                        `revokedAt` INTEGER NOT NULL,
                        `transferredTo` TEXT NOT NULL DEFAULT '',
                        PRIMARY KEY(`deviceId`)
                    )
                """.trimIndent())
            }
        }

        // Migration from v6 → v7: change DebtHistory.id from Long autoGenerate to String UUID
        // This prevents the "history shows 5 times" duplication bug caused by Firestore
        // syncing id=0 back to Room, where autoGenerate creates a new row each time.
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `debt_history_new` (
                        `id` TEXT NOT NULL,
                        `debtId` TEXT NOT NULL,
                        `actionType` TEXT NOT NULL,
                        `oldAmount` REAL NOT NULL,
                        `newAmount` REAL NOT NULL,
                        `changedByUserId` TEXT NOT NULL,
                        `notes` TEXT NOT NULL DEFAULT '',
                        `changedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                // Convert existing numeric IDs to strings — they'll keep working as
                // primary keys and match existing Firestore doc IDs.
                db.execSQL("""
                    INSERT INTO debt_history_new
                    SELECT CAST(id AS TEXT), debtId, actionType, oldAmount, newAmount,
                           changedByUserId, notes, changedAt
                    FROM debt_history
                """.trimIndent())
                db.execSQL("DROP TABLE debt_history")
                db.execSQL("ALTER TABLE debt_history_new RENAME TO debt_history")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sajil_alduyun_db"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .fallbackToDestructiveMigration(false)  // Safety net for very old DB versions (v1/v2)
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            // Fresh install: seed default plans
                            db.execSQL("INSERT OR IGNORE INTO plans (id, name, durationDays, maxAmount, isActive) VALUES (1, '30 يوم', 30, 100000.0, 1)")
                            db.execSQL("INSERT OR IGNORE INTO plans (id, name, durationDays, maxAmount, isActive) VALUES (2, 'مفتوح', 0, 25000.0, 1)")
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
