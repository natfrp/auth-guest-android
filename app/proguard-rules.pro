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
-keepclasseswithmembers class com.google.android.gms.common.internal.*
-keepclasseswithmembers public abstract class com.google.android.gms.internal.mlkit_vision_barcode.*
-keepclasseswithmembers final class com.google.android.gms.internal.mlkit_vision_barcode.*
-keepclasseswithmembers class com.google.android.gms.internal.mlkit_vision_barcode.zznw
-keepclasseswithmembers class com.google.android.gms.internal.mlkit_vision_barcode.*
-keepclasseswithmembers class com.google.android.gms.internal.mlkit_vision_common.*
-keepclasseswithmembers class java.lang.Object
-keep public abstract class com.google.mlkit.common.sdkinternal.LazyInstanceMap {
    *;
}
-keepclasseswithmembers class com.google.mlkit.vision.barcode.internal.*
-keepclasseswithmembers class com.natfrp.authguest.MLKitTrickery

-dontwarn com.google.android.datatransport.Encoding
-dontwarn com.google.android.datatransport.Event
-dontwarn com.google.android.datatransport.Transformer
-dontwarn com.google.android.datatransport.Transport
-dontwarn com.google.android.datatransport.TransportFactory
-dontwarn com.google.android.datatransport.cct.CCTDestination
-dontwarn com.google.android.datatransport.runtime.Destination
-dontwarn com.google.android.datatransport.runtime.TransportRuntime
-dontwarn com.google.firebase.encoders.DataEncoder
-dontwarn com.google.firebase.encoders.EncodingException
-dontwarn com.google.firebase.encoders.FieldDescriptor$Builder
-dontwarn com.google.firebase.encoders.FieldDescriptor
-dontwarn com.google.firebase.encoders.ObjectEncoder
-dontwarn com.google.firebase.encoders.ObjectEncoderContext
-dontwarn com.google.firebase.encoders.ValueEncoderContext
-dontwarn com.google.firebase.encoders.annotations.Encodable
-dontwarn com.google.firebase.encoders.annotations.ExtraProperty
-dontwarn com.google.firebase.encoders.config.Configurator
-dontwarn com.google.firebase.encoders.config.EncoderConfig
-dontwarn com.google.firebase.encoders.json.JsonDataEncoderBuilder