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
-dontobfuscate
-keep,allowoptimization class is.xyz.mpv.** { public protected *; }

# W31.14 release: 跳过可选依赖的 missing class 警告
# commons-compress 7z 解压走 tukaani.xz(运行时反射找类)
-dontwarn org.tukaani.xz.**
-dontwarn org.apache.commons.compress.archivers.sevenz.**
-dontwarn java.lang.invoke.StringConcatFactory
# mbassy 事件总线支持 javax.el 表达式过滤(我们用不上 EL filter)
-dontwarn javax.el.**
# smbj SMB Kerberos 认证(我们只用 NTLM/guest,不需要 GSS)
-dontwarn org.ietf.jgss.**