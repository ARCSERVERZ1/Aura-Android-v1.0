# Preserve Gson / Retrofit telemetry payload models
-keep class com.aura.family.data.remote.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
