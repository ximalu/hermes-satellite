# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.hermes.satellite.** { *; }

# JSch (SSH client)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn com.jcraft.jzlib.**
-dontwarn org.ietf.jgss.**

# Apache MINA SSHD
-keep class org.apache.sshd.** { *; }
-dontwarn org.apache.sshd.**

# SLF4J for Android
-keep class org.slf4j.** { *; }

# OkHttp / Okio
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# JSON (org.json)
-keep class org.json.** { *; }
