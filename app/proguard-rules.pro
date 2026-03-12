-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep all model and data classes (they are serialized with kotlinx.serialization)
-keep class com.ataraxiagoddess.budgetbrewer.data.** { *; }

# Keep Supabase classes
-keep class io.github.jan.supabase.** { *; }

# Keep Room database classes
-keep class com.ataraxiagoddess.budgetbrewer.database.** { *; }
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Keep Kotlin metadata (used by some reflection-based libraries)
-keep class kotlin.Metadata { *; }

# Keep coroutines internals (prevents obscure crashes)
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }

# Keep MPAndroidChart classes
-keep class com.github.mikephil.charting.** { *; }

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.concurrent.GuardedBy
-dontwarn org.slf4j.impl.StaticLoggerBinder