# Line numbers for crash reports (Sentry)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Methods called from JavaScript in the shop, portal and event pages
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Decompose saves the overlay stack with kotlinx.serialization
-keep,includedescriptorclasses class com.itd.app.decompose.**$$serializer { *; }
-keepclassmembers class com.itd.app.decompose.** {
    *** Companion;
}
