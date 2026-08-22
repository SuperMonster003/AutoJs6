# Compiled user classes link to this ABI by binary name across a disposable ClassLoader.
# R8 cannot see those future callers, so the entry surface and its default-method holder
# must remain stable in every host/provider release build.
-keep public interface org.autojs.plugin.jvmsource.api.AutoJsJvmEntry { public *; }
-keep public interface org.autojs.plugin.jvmsource.api.JvmScriptContext { public *; }
-keep public interface org.autojs.plugin.jvmsource.api.JvmAppApi { public *; }
-keep public interface org.autojs.plugin.jvmsource.api.JvmCancellation { public *; }
-keep public class org.autojs.plugin.jvmsource.api.JvmCancellationException { public protected *; }
-keep class org.autojs.plugin.jvmsource.api.JvmScriptContext$DefaultImpls { *; }
-keepattributes Exceptions,InnerClasses,EnclosingMethod,Signature,*Annotation*
