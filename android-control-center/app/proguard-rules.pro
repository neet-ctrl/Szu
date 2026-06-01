-keepattributes Signature,*Annotation*,EnclosingMethod,InnerClasses
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }

# LibSU
-keep class com.topjohnwu.superuser.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Hilt
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Kotlin serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keepclassmembers @kotlinx.serialization.Serializable class ** { *** Companion; }

# Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * { @com.squareup.moshi.FromJson *; @com.squareup.moshi.ToJson *; }

# Compose
-keep class androidx.compose.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }

# Reflect
-keep class kotlin.reflect.jvm.internal.** { *; }

# App Manager / Package Manager bridge
-keep class android.content.pm.** { *; }
-keep class android.app.usage.** { *; }

# Audio DSP (JamesDSP)
-keep class james.dsp.** { *; }

# Accessibility
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Key event bridge
-keep class android.hardware.input.** { *; }
-keep class android.view.InputEvent { *; }

# dadb (pure-Kotlin ADB protocol — pairing + connection without adb binary)
-keep class dadb.** { *; }
-dontwarn dadb.**
-keep class com.android.** { *; }

# General
-keepclassmembers enum * { public static **[] values(); public static ** valueOf(java.lang.String); }
-keep class * implements android.os.Parcelable { public static final android.os.Parcelable$Creator *; }
-keepclassmembers class **.R$* { public static <fields>; }
