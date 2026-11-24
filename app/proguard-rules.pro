# JNI entry points are looked up by name.
-keep class app.ocarinatuner.audio.NativeEngine { native <methods>; *; }

# kotlinx.serialization models in :core
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class app.ocarinatuner.core.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.ocarinatuner.core.**$$serializer { *; }
-dontnote kotlinx.serialization.**
