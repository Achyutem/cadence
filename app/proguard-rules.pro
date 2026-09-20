# Room generated code is reflection-free; nothing special required.
# Keep kotlinx.serialization metadata for @Serializable classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class dev.achyutem.cadence.** {
    *** Companion;
}
-keepclasseswithmembers class dev.achyutem.cadence.** {
    kotlinx.serialization.KSerializer serializer(...);
}
