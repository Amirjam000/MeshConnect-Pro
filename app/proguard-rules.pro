# Proguard rules for MeshConnect-Pro
-keep class com.meshconnect.pro.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
