# ── MerlotTV ProGuard / R8 Rules ──────────────────────────────────────────────

# Keep JSON model classes used by Moshi/Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.merlottv.kotlin.data.model.** { *; }
-keep class com.merlottv.kotlin.domain.model.** { *; }

# ── Moshi ─────────────────────────────────────────────────────────────────────
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers @com.squareup.moshi.JsonClass class * extends java.lang.Enum {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
# Moshi-kotlin-codegen generated adapters
-keep class **JsonAdapter {
    <init>(...);
    <fields>;
}
-keepnames @com.squareup.moshi.JsonClass class *

# ── Retrofit ──────────────────────────────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# ── OkHttp ────────────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ── Hilt / Dagger ─────────────────────────────────────────────────────────────
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ── Firebase ──────────────────────────────────────────────────────────────────
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ── Coil ──────────────────────────────────────────────────────────────────────
-dontwarn coil.**

# ── Compose ───────────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Media3 / ExoPlayer ───────────────────────────────────────────────────────
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# ── LibVLC ────────────────────────────────────────────────────────────────────
-keep class org.videolan.** { *; }
-dontwarn org.videolan.**

# ── ZXing (QR codes) ─────────────────────────────────────────────────────────
-keep class com.google.zxing.** { *; }

# ── Kotlin coroutines ─────────────────────────────────────────────────────────
-dontwarn kotlinx.coroutines.**

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# ── General ───────────────────────────────────────────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
