# R8 / ProGuard rules for Open Benches release builds.
# After bundleRelease, upload the mapping file to Play Console:
#   app/build/outputs/mapping/release/mapping.txt
# Release → App bundle explorer → [version] → Downloads → Upload ReTrace mapping file

# Readable stack traces in Play Console / crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# --- Gson (API + local cache JSON) ---
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class org.openbenches.data.** { *; }

# --- Retrofit / OkHttp ---
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization interface <1>
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>
-keep interface org.openbenches.data.OpenBenchesService { *; }

# --- BuildConfig (Auth0 etc.) ---
-keep class org.openbenches.BuildConfig { *; }

# --- osmdroid map ---
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# --- OpenCV (native OCR preprocessing) ---
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# --- PaddleOCR official Android ONNX SDK + ONNX Runtime ---
-keep class com.paddle.ocr.** { *; }
-keep class ai.onnxruntime.** { *; }
-dontwarn ai.onnxruntime.**

# Auth0 ships consumer ProGuard rules in its AAR.
