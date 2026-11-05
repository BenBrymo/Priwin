# Release build (R8). Compose and AndroidX ship their own rules; these are safety nets.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-dontwarn kotlin.**
-dontwarn org.jetbrains.annotations.**
