-keep class com.chaquo.python.** { *; }
-keep class org.johnbrenof.dowtube.download.DownloadListener { *; }

-keepclasseswithmembers class * implements org.johnbrenof.dowtube.download.DownloadListener {
    public *;
}
