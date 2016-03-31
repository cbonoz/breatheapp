#-optimizations "*"
-optimizationpasses 64



-keep class me.denley.courier.** { *; }
-dontwarn me.denley.courier.compiler.**
-keep class **$$Delivery { *; }
-keep class **DataMapPackager { *; }
-keepclasseswithmembernames class * {
    @me.denley.courier.* <fields>;
}
-keepclasseswithmembernames class * {
    @me.denley.courier.* <methods>;
}
-keep @me.denley.courier.* public class * { *; }
