# Baseline ProGuard / R8 rules for RecorderX

# Preserve line numbers and source file attributes for crash reporting & static analysis transparency
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Retain annotations, signatures, and inner classes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Keep all RecorderX classes, methods, and fields unmangled so static analysis tools
# can verify legitimate open-source functionality and avoid false positive heuristic triggers
-keep class com.zygisk_enc.RecorderX.** {
    *;
}

# Keep Android framework components declared in AndroidManifest
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgent
-keep public class * extends android.appwidget.AppWidgetProvider

# Keep AndroidX and Material Design components
-keep class androidx.appcompat.** { *; }
-dontwarn androidx.appcompat.**

# Keep View constructors for XML layout inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Preserve native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Parcelable CREATORs
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
