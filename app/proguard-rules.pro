# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# ---------------------------------------------
# Room Database
# ---------------------------------------------
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * {
    @androidx.room.Insert <methods>;
    @androidx.room.Update <methods>;
    @androidx.room.Delete <methods>;
    @androidx.room.Query <methods>;
}

# ---------------------------------------------
# Kotlinx Serialization (Supabase Payloads)
# ---------------------------------------------
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}

# ---------------------------------------------
# Coroutines
# ---------------------------------------------
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ---------------------------------------------
# MPAndroidChart
# (Official recommendation requires broad keep due to internal reflection)
-keep class com.github.mikephil.charting.** { *; }
-keepclassmembers class com.github.mikephil.charting.** { *; }

# ---------------------------------------------
# Timber
# ---------------------------------------------
-assumenosideeffects class timber.log.Timber {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}

-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-keep class javax.annotation.** { *; }
-keep class org.slf4j.** { *; }

# Keep all Supabase classes
-keep class io.github.jan.supabase.** { *; }
-keep interface io.github.jan.supabase.** { *; }

# Keep Kotlinx AtomicFU
-keep class kotlinx.atomicfu.** { *; }

# Keep Kotlinx DateTime and its serializers
-keep class kotlinx.datetime.** { *; }
-keep class kotlinx.datetime.serializers.** { *; }

# Keep Kermit logging (if used by Supabase)
-keep class io.github.jan.supabase.logging.** { *; }

# Keep all serializable companion objects and synthetic serializers
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    static final kotlinx.serialization.KSerializer $serializer;
}

# Keep default constructors for serializable classes
-keepclassmembers class * {
    public <init>();
}

-dontwarn io.github.jan.supabase.logging.KermitSupabaseLogger
-dontwarn kotlinx.datetime.Clock$System
-dontwarn kotlinx.datetime.Instant$Companion
-dontwarn kotlinx.datetime.Instant
-dontwarn kotlinx.datetime.serializers.InstantIso8601Serializer