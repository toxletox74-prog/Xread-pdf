# ML Kit et Compose embarquent leurs propres règles consumer.

# PdfBox-Android : chargement de ressources (polices, encodages) par réflexion
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**
