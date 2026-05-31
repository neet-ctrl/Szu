# ACCU SDK Test App — ProGuard Rules
# Keep AIDL-generated classes
-keep class com.accu.api.** { *; }
-keep class com.accu.sdk.** { *; }
-keepclassmembers class * implements android.os.IInterface { *; }
-keepclassmembers class * extends android.os.Binder { *; }
