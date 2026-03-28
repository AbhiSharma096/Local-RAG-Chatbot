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
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# --- LOCAL RAG CHATBOT RULES ---

# PDFBox warnings
-dontwarn com.gemalto.jp2.JP2Decoder

# Google MediaPipe/AutoValue warnings
-dontwarn com.google.auto.value.AutoValue$Builder
-dontwarn com.google.auto.value.AutoValue

# Prevent R8 from stripping ObjectBox database generated files
-keep class io.objectbox.** { *; }
-dontwarn io.objectbox.**

# Prevent R8 from breaking MediaPipe GenAI C++ hooks
-keep class com.google.mediapipe.tasks.genai.** { *; }
-dontwarn com.google.mediapipe.framework.image.**
-keepclassmembers class * {
    @com.google.mediapipe.framework.UsedByNative *;
}

# Prevent R8 from breaking ONNX Runtime C++ hooks
-keep class ai.onnxruntime.** { *; }