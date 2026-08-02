package com.sajilalduyun.app.service

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.firestoreSettings
import com.sajilalduyun.app.database.AppDatabase
import com.sajilalduyun.app.model.CustomerDebt
import com.sajilalduyun.app.model.DebtHistory
import com.sajilalduyun.app.model.DebtStatus
import com.sajilalduyun.app.model.PaymentPlan
import com.sajilalduyun.app.model.User
import com.sajilalduyun.app.model.UserRole
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Bridges the local Room database with Firebase Firestore for
 * real-time cross-device sync.
 *
 * Architecture:
 *   Room ──local writes──► SyncService ──► Firestore (push)
 *   Firestore ──snapshot listener──► SyncService ──► Room (pull)
 *
 * Room is always the single source of truth. The app continues to
 * work fully offline — Firestore syncs in the background whenever
 * connectivity is available.
 *
 * Requires google-services.json in the app/ module directory with
 * your Firebase project configuration.
 */
object SyncService {

    private const val TAG = "SyncService"

    // Firestore collection names
    private const val COL_USERS  = "users"
    private const val COL_DEBTS  = "debts"
    private const val COL_HISTORY = "debt_history"
    private const val COL_PLANS  = "plans"
    private const val COL_LICENSES = "licenses"
    private const val COL_UID_MAP = "uid_mappings"

    // ── State ──────────────────────────────────────────────────────────────

    private var firestore: FirebaseFirestore? = null
    private var auth: FirebaseAuth? = null
    private var initialized = false
    private var initializing = false
    private var listening = false

    /** A scope tied to this singleton for executing Room writes. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Firestore listener registrations — cleared on stopListening(). */
    private val listenerRegistrations = mutableListOf<() -> Unit>()

    /** Throttle: last time onDataChanged was invoked (ms). */
    private var lastNotifyTime = 0L

    /**
     * Optional callback fired whenever data arrives from Firestore and is
     * written to Room, throttled to at most once per 800ms.
     * DashboardActivity sets this so it can re-query Room and refresh UI.
     */
    var onDataChanged: (() -> Unit)? = null

    // ── Initialization ─────────────────────────────────────────────────────

    /**
     * Initializes Firebase using google-services.json metadata and
     * signs in anonymously so Firestore operations are authenticated.
     *
     * Safe to call multiple times — repeats if initialization failed before.
     */
    fun initialize(context: Context) {
        if (initialized) return
        if (initializing) return  // prevent concurrent init attempts
        initializing = true

        try {
            // FirebaseApp reads config from google-services.json automatically
            FirebaseApp.initializeApp(context)

            firestore = FirebaseFirestore.getInstance().apply {
                firestoreSettings = firestoreSettings {
                    isPersistenceEnabled = true  // offline cache
                }
            }

            auth = FirebaseAuth.getInstance()

            // Sign in anonymously (persists across app restarts)
            if (auth?.currentUser == null) {
                auth?.signInAnonymously()?.addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.i(TAG, "Anonymous auth OK: uid=${task.result?.user?.uid}")
                    } else {
                        Log.e(TAG, "Anonymous auth failed — will retry on next sync", task.exception)
                    }
                }
            }

            initialized = true
            Log.i(TAG, "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase init failed — will retry on next sync", e)
        } finally {
            initializing = false
        }
    }

    // ── Real-time listeners (remote → local) ──────────────────────────────

    /**
     * Starts listening to all Firestore collections and writes remote
     * changes into Room. Call once after the user has authenticated
     * locally (e.g. on dashboard load).
     *
     * Only listens while the app is active. Call [stopListening] in
     * onPause (optional — the SDK manages reconnects).
     */
    fun startListening(context: Context) {
        if (listening) return
        val db = firestore ?: return
        listening = true

        listenToCollection(context, db, COL_USERS)  { doc -> docToUser(doc) }
        listenToCollection(context, db, COL_DEBTS)  { doc -> docToDebt(doc) }
        listenToCollection(context, db, COL_HISTORY) { doc -> docToHistory(doc) }
        listenToCollection(context, db, COL_PLANS)   { doc -> docToPlan(doc) }

        Log.i(TAG, "Real-time listeners started")
    }

    fun stopListening() {
        listenerRegistrations.forEach { it() }
        listenerRegistrations.clear()
        listening = false
        Log.i(TAG, "Real-time listeners stopped")
    }

    // ── Push: local writes → Firestore ────────────────────────────────────

    /** Write (or overwrite) a User document in Firestore. */
    fun syncUser(user: User) {
        val db = firestore ?: return
        db.collection(COL_USERS).document(user.id)
            .set(userToMap(user), SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "syncUser failed", e) }
    }

    fun deleteUser(userId: String) {
        firestore?.collection(COL_USERS)?.document(userId)?.delete()
    }

    /** Write (or overwrite) a CustomerDebt document in Firestore. */
    fun syncDebt(debt: CustomerDebt) {
        val db = firestore ?: return
        db.collection(COL_DEBTS).document(debt.id)
            .set(debtToMap(debt), SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "syncDebt failed", e) }
    }

    fun deleteDebt(debtId: String) {
        firestore?.collection(COL_DEBTS)?.document(debtId)?.delete()
    }

    /** Write a DebtHistory entry to Firestore (uses Room ID as document ID). */
    fun syncDebtHistory(entry: DebtHistory) {
        val db = firestore ?: return
        db.collection(COL_HISTORY)
            .document(entry.id.toString())
            .set(historyToMap(entry), SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "syncDebtHistory failed", e) }
    }

    /** Write (or overwrite) a PaymentPlan document in Firestore. */
    fun syncPlan(plan: PaymentPlan) {
        val db = firestore ?: return
        db.collection(COL_PLANS).document(plan.id.toString())
            .set(planToMap(plan), SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "syncPlan failed", e) }
    }

    fun deletePlan(planId: Long) {
        firestore?.collection(COL_PLANS)?.document(planId.toString())?.delete()
    }

    // ── License sync (cloud recovery) ──────────────────────────────────────

    /** Save license activation to Firestore so the owner can recover it on a new device. */
    fun syncLicense(ownerId: String, startDate: Long, durationMs: Long, deviceId: String) {
        val db = firestore ?: return
        val data = mapOf<String, Any>(
            "ownerId" to ownerId,
            "active" to true,
            "licenseStartDate" to startDate,
            "licenseDurationMs" to durationMs,
            "deviceId" to deviceId,
            "updatedAt" to System.currentTimeMillis()
        )
        db.collection(COL_LICENSES).document(ownerId)
            .set(data, SetOptions.merge())
            .addOnFailureListener { e -> Log.e(TAG, "syncLicense failed", e) }
    }

    /** Check Firestore for an existing user by ID. Returns null if none found. */
    suspend fun getUserFromCloud(userId: String): User? {
        val db = firestore ?: return null
        return try {
            val doc = db.collection(COL_USERS).document(userId).get().await()
            if (doc.exists()) docToUser(doc) else null
        } catch (e: Exception) {
            Log.e(TAG, "getUserFromCloud failed", e)
            null
        }
    }

    /**
     * Creates a uid_mapping document that bridges this device's Firebase
     * anonymous auth UID to the app-level userId and role.
     *
     * This document is required by the Firestore security rules so that
     * per-user access control can work with anonymous auth.
     * Call this AFTER the user is created in Room but BEFORE fullSync.
     */
    suspend fun createUidMapping(appUserId: String, role: String) {
        val db = firestore ?: return
        val uid = auth?.currentUser?.uid ?: return
        try {
            db.collection(COL_UID_MAP).document(uid)
                .set(mapOf("appUserId" to appUserId, "role" to role))
                .await()
            Log.i(TAG, "UID mapping created: firebase=$uid → app=$appUserId ($role)")
        } catch (e: Exception) {
            Log.e(TAG, "createUidMapping failed", e)
        }
    }

    /** Check Firestore for an existing active license for this owner. Returns null if none found. */
    suspend fun getLicenseFromCloud(ownerId: String): Map<String, Any>? {
        val db = firestore ?: return null
        return try {
            val doc = db.collection(COL_LICENSES).document(ownerId).get().await()
            if (doc.exists() && doc.getBoolean("active") == true) doc.data else null
        } catch (e: Exception) {
            Log.e(TAG, "getLicenseFromCloud failed", e)
            null
        }
    }

    // ── Full sync (on-demand) ──────────────────────────────────────────────

    /**
     * Pulls ALL data from Firestore and writes it to Room.
     * Useful on first launch after a worker registers, or as a
     * fallback if the real-time listener missed something.
     */
    fun fullSync(context: Context) {
        val fs = firestore ?: return
        scope.launch {
            try {
                Log.i(TAG, "Full sync started")
                pullCollection(context, fs, COL_USERS)  { doc -> docToUser(doc) }
                pullCollection(context, fs, COL_DEBTS)  { doc -> docToDebt(doc) }
                pullCollection(context, fs, COL_HISTORY) { doc -> docToHistory(doc) }
                pullCollection(context, fs, COL_PLANS)   { doc -> docToPlan(doc) }
                Log.i(TAG, "Full sync complete")
            } catch (e: Exception) {
                Log.e(TAG, "Full sync failed", e)
            }
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /** Sets up a snapshot listener for one collection. */
    private fun listenToCollection(
        context: Context,
        db: FirebaseFirestore,
        collection: String,
        toEntity: (DocumentSnapshot) -> Any?
    ) {
        val reg = db.collection(collection)
            .addSnapshotListener { snapshots, error ->
                if (error != null) {
                    Log.w(TAG, "Listener error on $collection", error)
                    return@addSnapshotListener
                }
                for (change in snapshots?.documentChanges ?: emptyList()) {
                    // Skip documents we just wrote ourselves
                    if (change.document.metadata.hasPendingWrites()) continue
                    scope.launch {
                        applyChange(context, change, toEntity)
                    }
                }
                // Notify the UI (e.g. DashboardActivity) to re-query Room
                val now = System.currentTimeMillis()
                if (now - lastNotifyTime > 800) {
                    lastNotifyTime = now
                    onDataChanged?.invoke()
                }
            }
        listenerRegistrations.add { reg.remove() }
    }

    /** Applies a single DocumentChange to Room. */
    private suspend fun applyChange(
        context: Context,
        change: DocumentChange,
        toEntity: (DocumentSnapshot) -> Any?
    ) {
        val entity = toEntity(change.document) ?: return
        val dao = AppDatabase.getDatabase(context)
        when (change.type) {
            DocumentChange.Type.ADDED,
            DocumentChange.Type.MODIFIED -> upsertInRoom(dao, entity)

            DocumentChange.Type.REMOVED  -> deleteFromRoom(dao, entity)
        }
    }

    /** Writes an entity into the correct Room table (INSERT / UPDATE). */
    private suspend fun upsertInRoom(dao: AppDatabase, entity: Any) {
        when (entity) {
            is User          -> dao.userDao().insertUser(entity)
            is CustomerDebt  -> dao.debtDao().insertDebt(entity)
            is DebtHistory   -> dao.debtHistoryDao().insert(entity)
            is PaymentPlan   -> dao.planDao().insert(entity)
        }
    }

    private suspend fun deleteFromRoom(dao: AppDatabase, entity: Any) {
        when (entity) {
            is User          -> dao.userDao().deleteUser(entity)
            is CustomerDebt  -> dao.debtDao().deleteDebt(entity)
            // DebtHistory doesn't need delete-from-sync
            is PaymentPlan   -> dao.planDao().deleteById(entity.id)
        }
    }

    /** Pulls all documents from one Firestore collection into Room. */
    private suspend fun pullCollection(
        context: Context,
        fs: FirebaseFirestore,
        collection: String,
        toEntity: (DocumentSnapshot) -> Any?
    ) {
        val snapshot = fs.collection(collection).get().await()
        val dao = AppDatabase.getDatabase(context)
        for (doc in snapshot.documents) {
            val entity = toEntity(doc) ?: continue
            upsertInRoom(dao, entity)
        }
    }

    // ── Mapping: Room entities ↔ Firestore maps ───────────────────────────

    private fun userToMap(user: User): Map<String, Any?> = mapOf(
        "id"           to user.id,
        "name"         to user.name,
        "role"         to user.role.name,
        "pin"          to user.pin,
        "phoneNumber"  to user.phoneNumber,
        "isActive"     to user.isActive
    )

    private fun docToUser(doc: DocumentSnapshot): User? {
        val id = doc.getString("id") ?: return null
        val roleStr = doc.getString("role") ?: return null
        return User(
            id          = id,
            name        = doc.getString("name") ?: "",
            role        = try { UserRole.valueOf(roleStr) } catch (_: Exception) { return null },
            pin         = doc.getString("pin") ?: "",
            phoneNumber = doc.getString("phoneNumber") ?: "",
            isActive    = doc.getBoolean("isActive") ?: false
        )
    }

    private fun debtToMap(debt: CustomerDebt): Map<String, Any?> = mapOf(
        "id"               to debt.id,
        "customerName"     to debt.customerName,
        "amount"           to debt.amount,
        "planId"           to debt.planId,
        "maxLimit"         to debt.maxLimit,
        "planDurationDays" to debt.planDurationDays,
        "status"           to debt.status.name,
        "createdByUserId"  to debt.createdByUserId,
        "createdAt"        to debt.createdAt.time,
        "lastUpdatedAt"    to debt.lastUpdatedAt.time
    )

    private fun docToDebt(doc: DocumentSnapshot): CustomerDebt? {
        val id = doc.getString("id") ?: return null
        val statusStr = doc.getString("status") ?: return null
        return CustomerDebt(
            id               = id,
            customerName     = doc.getString("customerName") ?: "",
            amount           = doc.getDouble("amount") ?: 0.0,
            planId           = doc.getLong("planId") ?: 1L,
            maxLimit         = doc.getDouble("maxLimit") ?: 0.0,
            planDurationDays = (doc.getLong("planDurationDays") ?: 0L).toInt(),
            status           = try { DebtStatus.valueOf(statusStr) } catch (_: Exception) { DebtStatus.PENDING },
            createdByUserId  = doc.getString("createdByUserId") ?: "",
            createdAt        = java.util.Date(doc.getLong("createdAt") ?: 0L),
            lastUpdatedAt    = java.util.Date(doc.getLong("lastUpdatedAt") ?: 0L)
        )
    }

    private fun historyToMap(entry: DebtHistory): Map<String, Any?> = mapOf(
        "debtId"          to entry.debtId,
        "actionType"      to entry.actionType,
        "oldAmount"       to entry.oldAmount,
        "newAmount"       to entry.newAmount,
        "changedByUserId" to entry.changedByUserId,
        "notes"           to entry.notes,
        "changedAt"       to entry.changedAt.time
    )

    private fun docToHistory(doc: DocumentSnapshot): DebtHistory? {
        val debtId = doc.getString("debtId") ?: return null
        return DebtHistory(
            id               = doc.id,
            debtId           = debtId,
            actionType       = doc.getString("actionType") ?: "",
            oldAmount        = doc.getDouble("oldAmount") ?: 0.0,
            newAmount        = doc.getDouble("newAmount") ?: 0.0,
            changedByUserId  = doc.getString("changedByUserId") ?: "",
            notes            = doc.getString("notes") ?: "",
            changedAt        = java.util.Date(doc.getLong("changedAt") ?: 0L)
        )
    }

    private fun planToMap(plan: PaymentPlan): Map<String, Any?> = mapOf(
        "id"           to plan.id,
        "name"         to plan.name,
        "durationDays" to plan.durationDays,
        "maxAmount"    to plan.maxAmount,
        "isActive"     to plan.isActive
    )

    private fun docToPlan(doc: DocumentSnapshot): PaymentPlan? {
        val name = doc.getString("name") ?: return null
        return PaymentPlan(
            id           = doc.getLong("id") ?: return null,
            name         = name,
            durationDays = (doc.getLong("durationDays") ?: 0L).toInt(),
            maxAmount    = doc.getDouble("maxAmount") ?: 0.0,
            isActive     = doc.getBoolean("isActive") ?: true
        )
    }
}
