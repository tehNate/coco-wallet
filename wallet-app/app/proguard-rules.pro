# Keep Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep Gson/JSON
-keep class org.json.** { *; }
-dontwarn org.json.**

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
