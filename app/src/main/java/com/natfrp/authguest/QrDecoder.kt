package com.natfrp.authguest

import androidx.camera.core.ImageProxy
import zxingcpp.BarcodeReader

/** Used on the camera analysis thread; the caller owns and closes each image. */
internal class QrDecoder {
    private val reader = BarcodeReader(BarcodeReader.Options(
        formats = setOf(BarcodeReader.Format.QR_CODE),
        tryHarder = true,
        tryRotate = true,
        tryInvert = true,
        tryDownscale = true,
        maxNumberOfSymbols = 1,
        textMode = BarcodeReader.TextMode.PLAIN
    ))
    private var useLocalAverage = false

    fun decode(image: ImageProxy): String? {
        // Like BinaryEye, try a different thresholding method on successive frames.
        useLocalAverage = !useLocalAverage
        reader.options.binarizer = if (useLocalAverage) {
            BarcodeReader.Binarizer.LOCAL_AVERAGE
        } else {
            BarcodeReader.Binarizer.GLOBAL_HISTOGRAM
        }
        // The upstream wrapper reads the Y plane directly, preserving stride/crop/rotation.
        return reader.read(image).firstOrNull { it.error == null && !it.text.isNullOrEmpty() }?.text
    }
}
