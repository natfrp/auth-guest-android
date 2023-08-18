package com.natfrp.authguest

import android.util.Log
import com.google.mlkit.common.sdkinternal.LazyInstanceMap
import java.lang.reflect.Field

// From https://stackoverflow.com/questions/71465067/disable-firebase-logging-for-google-ml-kit-library-in-android

/**
 * This class tries to disable MLKit's phoning home/logging.
 * This is extremely hacky and will probably break in the next update (obfuscated class names will probably need renaming).
 *
 * This class exploits the fact, that there are multiple options classes which control this
 * (look for "MLKitLoggingOptions" in toString implementation) and for some reason MLKit uses them as keys
 * in LazyInstanceMaps which exist as static (usually) variables (which are themselves lazy).
 *
 * This makes sure that the LazyInstanceMaps exist, then it hijacks their internal HashMap implementation
 * and replaces it with a custom map, that creates instances of whatever with logging disabled.
 *
 * The way to detect which holder classes need renaming, look at the stack trace, for example:
 * ```
java.lang.NoClassDefFoundError: Failed resolution of: Lcom/google/android/datatransport/cct/CCTDestination;
at com.google.android.gms.internal.mlkit_vision_barcode.zznu.<init>(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:1)
at com.google.android.gms.internal.mlkit_vision_barcode.zznf.<init>(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:3)
at com.google.android.gms.internal.mlkit_vision_barcode.zznw.create(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:4)
at com.google.mlkit.common.sdkinternal.LazyInstanceMap.get(com.google.mlkit:common@@18.0.0:3)
at com.google.android.gms.internal.mlkit_vision_barcode.zznx.zza(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:2)
at com.google.android.gms.internal.mlkit_vision_barcode.zznx.zzb(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:3)
at com.google.mlkit.vision.barcode.internal.zzf.create(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:3)
at com.google.mlkit.common.sdkinternal.LazyInstanceMap.get(com.google.mlkit:common@@18.0.0:3)
at com.google.mlkit.vision.barcode.internal.zze.zzb(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:2)
at com.google.mlkit.vision.barcode.BarcodeScanning.getClient(com.google.android.gms:play-services-mlkit-barcode-scanning@@18.0.0:3)
 * ```
 * here are two LazyInstanceMap lookups, of which only the second one (through trial and error or with debugger)
 * uses MLKitLoggingOptions keys. From here we can find that the holder class is com.google.android.gms.internal.mlkit_vision_barcode.zznx .
 */
object MLKitTrickery {

    private class mlkit_vision_barcodeLoggingOptions(base: com.google.android.gms.internal.mlkit_vision_barcode.zzne) :
        com.google.android.gms.internal.mlkit_vision_barcode.zzne() {
        private val libraryName: String = base.zzb()
        private val firelogEventType: Int = base.zza()
        override fun zza(): Int = firelogEventType
        override fun zzb(): String = libraryName
        override fun zzc(): Boolean = false //enableFirelog

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as mlkit_vision_barcodeLoggingOptions
            if (libraryName != other.libraryName) return false
            if (firelogEventType != other.firelogEventType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = libraryName.hashCode()
            result = 31 * result + firelogEventType
            return result
        }
    }

    private class mlkit_vision_commonLoggingOptions(base: com.google.android.gms.internal.mlkit_vision_common.zzjn) :
        com.google.android.gms.internal.mlkit_vision_common.zzjn() {
        private val libraryName: String = base.zzb()
        private val firelogEventType: Int = base.zza()
        override fun zza(): Int = firelogEventType
        override fun zzb(): String = libraryName
        override fun zzc(): Boolean = false //enableFirelog

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as mlkit_vision_commonLoggingOptions
            if (libraryName != other.libraryName) return false
            if (firelogEventType != other.firelogEventType) return false
            return true
        }

        override fun hashCode(): Int {
            var result = libraryName.hashCode()
            result = 31 * result + firelogEventType
            return result
        }
    }

    private fun isMLKitLoggingOptions(obj: Any): Boolean {
        return obj is com.google.android.gms.internal.mlkit_vision_barcode.zzne
                || obj is com.google.android.gms.internal.mlkit_vision_common.zzjn
    }

    private fun convertMLKitLoggingOptions(obj: Any): Any? {
        if (obj is com.google.android.gms.internal.mlkit_vision_barcode.zzne) {
            return mlkit_vision_barcodeLoggingOptions(obj)
        }
        if (obj is com.google.android.gms.internal.mlkit_vision_common.zzjn) {
            return mlkit_vision_commonLoggingOptions(obj)
        }
        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun patchLazyMap(lazyMapHolder: Any?, lazyMapHolderClass: Class<*>) {
        val holderField =
            lazyMapHolderClass.declaredFields.find { LazyInstanceMap::class.java.isAssignableFrom(it.type) }!!
        holderField.isAccessible = true
        var currentLazyInstanceMap = holderField.get(lazyMapHolder)
        if (currentLazyInstanceMap == null) {
            var lastError: Throwable? = null
            for (constructor in holderField.type.declaredConstructors) {
                try {
                    constructor.isAccessible = true
                    val params = arrayOfNulls<Any?>(constructor.parameterCount)
                    currentLazyInstanceMap = constructor.newInstance(*params)
                    holderField.set(lazyMapHolder, currentLazyInstanceMap)
                } catch (e: Throwable) {
                    lastError = e
                }
            }
            if (currentLazyInstanceMap == null) {
                throw java.lang.Exception(
                    "Failed to initialize LazyInstanceMap " + holderField.type,
                    lastError
                )
            }
        }

        var mapHolderClass: Class<*> = currentLazyInstanceMap.javaClass
        val createMethod = mapHolderClass.getDeclaredMethod("create", Object::class.java)

        val mapField: Field
        while (true) {
            val mapFieldCandidate =
                mapHolderClass.declaredFields.firstOrNull { Map::class.java.isAssignableFrom(it.type) }
            if (mapFieldCandidate != null) {
                mapField = mapFieldCandidate
                break
            }
            mapHolderClass = mapHolderClass.superclass
                ?: error("It appears that ${currentLazyInstanceMap.javaClass} does not have a backing map field")
        }

        mapField.isAccessible = true
        val oldMap = mapField.get(currentLazyInstanceMap) as MutableMap<Any, Any?>
        val customMap = object : MutableMap<Any, Any?> by oldMap {

            override fun containsKey(key: Any): Boolean {
                if (oldMap.containsKey(key)) {
                    return true
                }
                if (isMLKitLoggingOptions(key)) {
                    return true
                }
                return false
            }

            override fun get(key: Any): Any? {
                val existing = oldMap.get(key)
                if (existing != null) {
                    return existing
                }

                val convertedKey = convertMLKitLoggingOptions(key)
                if (convertedKey != null) {
                    createMethod.isAccessible = true
                    val created = createMethod.invoke(currentLazyInstanceMap, convertedKey)
                    oldMap.put(key, created)
                    return created
                }

                return null
            }
        }
        mapField.set(currentLazyInstanceMap, customMap)
    }

    private var initialized = false

    /**
     * Call this to attempt to disable MLKit logging.
     */
    fun init() {
        if (initialized) {
            return
        }
        try {
            patchLazyMap(
                null,
                com.google.android.gms.internal.mlkit_vision_barcode.zznx::class.java
            )
            patchLazyMap(null, com.google.android.gms.internal.mlkit_vision_common.zzkc::class.java)
            initialized = true
        } catch (e: Throwable) {
            Log.e("MLKitTrickery", "Failed to disable MLKit phoning home")
        }
    }
}