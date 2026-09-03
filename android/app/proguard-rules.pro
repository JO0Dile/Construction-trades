# Room generates implementations reflectively referenced by the runtime.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# kotlinx.serialization keeps its generated serializers on the companion.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class il.co.tradesmanager.** {
    *** Companion;
}
-keepclasseswithmembers class il.co.tradesmanager.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class il.co.tradesmanager.**$$serializer { *; }

# SQLCipher loads its native bridge by name.
-keep class net.zetetic.database.** { *; }
-dontwarn net.zetetic.database.**

# ML Kit barcode models.
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Crash reports should still name our own classes.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
