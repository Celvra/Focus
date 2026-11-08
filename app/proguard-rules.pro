-keep,allowoptimization,allowobfuscation public class fan.x0.focus.hijack.FocusHijackModule {
    public <init>();
}
-keep class fan.x0.focus.hijack.** { *; }
-dontwarn io.github.libxposed.api.**
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
