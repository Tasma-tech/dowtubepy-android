# Regras do R8/ProGuard.
# O Chaquopy gera código que reflete sobre as classes do interpretador Python;
# caso o minify seja habilitado no futuro, mantenha as classes do plugin.

-keep class com.chaquo.python.** { *; }
-keep class org.johnbrenof.dowtube.download.DownloadListener { *; }

# Ponte Kotlin <-> Python: os métodos da interface DownloadListener são
# chamados por reflexão a partir do código Python.
-keepclasseswithmembers class * implements org.johnbrenof.dowtube.download.DownloadListener {
    public *;
}
