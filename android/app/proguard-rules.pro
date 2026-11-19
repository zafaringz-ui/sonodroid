# Keep the UniFFI-generated bindings when the Rust core is enabled.
-keep class dev.sonora.core.ffi.** { *; }
-keep class dev.sonora.core.rust.** { *; }

# JNA-free UniFFI still uses JNI callbacks; keep the listener implementations.
-keepclassmembers class * implements dev.sonora.core.ffi.CoreListener { *; }
-keepclassmembers class * implements dev.sonora.core.ffi.TokenMinter { *; }

# Kotlin serialization for type-safe navigation routes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class dev.sonora.** { *; }

# Coil
-dontwarn coil3.**
