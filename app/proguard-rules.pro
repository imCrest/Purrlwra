-dontwarn org.mozilla.javascript.**
-dontwarn android.app.AndroidAppHelper
-dontwarn java.lang.reflect.AnnotatedType

-keep class com.tonyodev.fetch2.** { *; }
-keep class com.tonyodev.fetch2core.** { *; }

-keep enum * { *; }

-keep class com.android.tools.smali.dexlib2.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class androidx.compose.material.icons.** { *; }
-keep class androidx.compose.material3.R$* { *; }
-keep class androidx.compose.ui.R$* { *; }
-keep class androidx.navigation.** { *; }
-keep,allowshrinking,allowobfuscation class me.eternal.purrfectsnap.** { *; }
-keep class androidx.core.content.res.ResourcesCompat { *; }

-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
# Prevent WorkManager from stripping generated Room database constructor
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

-keep class android.support.annotation.** { *; }
-dontwarn android.support.annotation.**
