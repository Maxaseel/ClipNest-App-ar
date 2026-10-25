# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# and hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep Room's generated implementation and database annotations discoverable.
-keep class com.example.data.local.AppDatabase { *; }
-keep class com.example.data.local.AppDatabase_Impl { *; }
-keep interface com.example.data.local.ClipDao { *; }
-keep class com.example.data.model.ClipItem { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }

# Keep Moshi generated adapters and reflective metadata used by JSON models.
-keep class **JsonAdapter { *; }
-keep class **JsonAdapter$* { *; }
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
}

# Preserve generic signatures and annotations needed by Retrofit/Moshi/Room.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Do not retain debug logging in optimized builds.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
