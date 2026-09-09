# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <methods>;
}
-keep,includedescriptorclasses class com.willykez.files.**$$serializer { *; }
-keepclassmembers class com.willykez.files.** {
    *** Companion;
}
-keepclasseswithmembers class com.willykez.files.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.Worker

# Keep model classes used for JSON (de)serialization
-keep class com.willykez.files.data.model.** { *; }
