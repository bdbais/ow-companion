# kotlinx.serialization: keep the generated serializers of the dataset model.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class com.bellizia.owcompanion.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.bellizia.owcompanion.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}
