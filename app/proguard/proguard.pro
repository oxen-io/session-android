-dontoptimize
-keepattributes SourceFile,LineNumberTable
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }
-keep class org.thoughtcrime.securesms.components.menu.** { *; }
-keep class org.session.** { *; }
-keepclassmembers class ** {
    public void onEvent*(**);
}
# Required for https://github.com/dmytrodanylyk/circular-progress-button
-keepclassmembers class com.dd.StrokeGradientDrawable {
    public void setStrokeColor(int);
}

