# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.retally.**$$serializer { *; }
-keepclassmembers class com.retally.** { *** Companion; }
-keepclasseswithmembers class com.retally.** { kotlinx.serialization.KSerializer serializer(...); }

# Ktor / OkHttp
-dontwarn org.slf4j.**
-dontwarn okhttp3.internal.platform.**

# Fragments are instantiated by name from res/navigation/nav_graph.xml.
-keep class com.retally.app.ui.**Fragment { <init>(); }
