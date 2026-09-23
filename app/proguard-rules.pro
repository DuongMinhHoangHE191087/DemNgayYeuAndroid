# ProGuard / R8 configuration for InLove production release

# 1. Keep Kotlin Coroutines & Flow
-keepattributes *Annotation*, InnerClasses, EnclosingMethod
-keepclassmembers class kotlinx.coroutines.** { *; }

# 2. Keep Room Database Entities and DAOs
-keep class androidx.room.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }

# 3. Keep Moshi and Retrofit JSON models
-keepattributes Signature
-keepclassmembers class * {
    @com.squareup.moshi.Json *;
    @com.squareup.moshi.JsonQualifier *;
}
-keep class com.squareup.moshi.** { *; }
-keep class retrofit2.** { *; }
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# 4. Keep Firebase models and Firestore POJOs
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName *;
    @com.google.firebase.firestore.Exclude *;
    @com.google.firebase.firestore.IgnoreExtraProperties *;
}
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**

# 5. Keep Cloudinary & InLove data models
-keep class com.example.data.model.** { *; }
-keep class com.example.data.cloudinary.** { *; }
-keep class com.example.data.firebase.** { *; }

# 6. Keep Google Play Billing Client and Google Mobile Ads
-keep class com.android.billingclient.api.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-dontwarn com.google.android.gms.ads.**

# 7. Preserve Line Numbers for Play Console Crash Reporting (De-obfuscation)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 8. Keep Firebase App Check Reflection Factories
-keep class com.google.firebase.appcheck.** { *; }
-keep class com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory { *; }
-keep class com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory { *; }
-keepclassmembers class com.google.firebase.appcheck.** {
    public static *** getInstance(...);
}
