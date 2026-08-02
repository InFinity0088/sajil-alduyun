# ── Firebase / Firestore ───────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── Room ───────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# ── Model classes (used for JSON / serialization) ──────────────────
-keep class com.sajilalduyun.app.model.** { *; }
-keep class com.sajilalduyun.app.database.** { *; }

# ── Coroutines ─────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ── RSA / Crypto ──────────────────────────────────────────────────
-keep class com.sajilalduyun.app.security.** { *; }
