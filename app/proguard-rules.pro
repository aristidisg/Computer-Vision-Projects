# Ensemble keeps its own model and engine classes intact: they are serialized to JSON
# (Room documents, DataStore settings, export zips) and the app is small enough not to care.
-keep class io.github.aristidisg.ensemble.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-dontwarn org.slf4j.**
