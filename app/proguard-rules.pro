# ===================================================================
# MAXIMUM SHRINKING & REPACKAGING
# ===================================================================
-allowaccessmodification
-repackageclasses ''
-optimizationpasses 7

# Suppress compile-time missing annotation warnings (prevents R8 build failure)
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn org.checkerframework.**

# ===================================================================
# STRIP METADATA & LOGGING (Reduces DEX size)
# ===================================================================
-renamesourcefileattribute ""
-keepattributes !SourceFile,!LineNumberTable,!LocalVariableTable,!LocalVariableTypeTable,!Directive
-keepattributes !RuntimeVisibleAnnotations,!RuntimeInvisibleAnnotations
-keepattributes !RuntimeVisibleParameterAnnotations,!RuntimeInvisibleParameterAnnotations
-keepattributes !Annotation,!EnclosingMethod,!InnerClasses,!Signature,!Exceptions

# Remove system logs in release build
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

# ===================================================================
# ANDROID COMPONENTS & WORKMANAGER
# ===================================================================
-keep public class * extends android.app.Application
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# Keep WorkManager Workers (Instantiated via Reflection)
-keep class * extends androidx.work.ListenableWorker

# ===================================================================
# KOTLIN COROUTINES & SERIALIZATION
# ===================================================================
# Prevents Dispatchers.Main crashes
-keepclassmembers class kotlinx.coroutines.android.HandlerDispatcher { 
    <init>(...); 
}

# Keeps JSON models & serialized companion code intact
-keep class com.willykez.files.data.model.** { *; }
-keepclassmembers class com.willykez.files.** {
    *** Companion;
}
-keepclasseswithmembers class com.willykez.files.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.willykez.files.**$$serializer { *; }
