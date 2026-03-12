# MPAndroidChart
-keep class com.github.mikephil.charting.** { *; }

# Protobuf
-keep class com.google.protobuf.** { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { *; }

# App models
-keep class com.lorascanner.app.model.** { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
