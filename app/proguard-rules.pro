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

# 6. Keep JavaMail / Activation for SMTP Email Sending
-keep class javax.mail.** { *; }
-keep class com.sun.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn javax.mail.**
-dontwarn com.sun.mail.**
-dontwarn javax.activation.**

# 7. Preserve Line Numbers for Play Console Crash Reporting (De-obfuscation)
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
