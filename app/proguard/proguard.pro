-dontoptimize
-keepattributes SourceFile,LineNumberTable
-keep class org.whispersystems.** { *; }
-keep class org.thoughtcrime.securesms.** { *; }
-keep class org.thoughtcrime.securesms.components.menu.** { *; }
-keep class org.session.** { *; }
-keepclassmembers class ** {
    public void onEvent*(**);
}

