package com.natfrp.authguest

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.CameraController.COORDINATE_SYSTEM_VIEW_REFERENCED
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.natfrp.authguest.databinding.ActivityWidgetQrScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class WidgetQrScanActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityWidgetQrScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_qr_scan)

        MLKitTrickery.init()

        viewBinding = ActivityWidgetQrScanBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        // Request camera permissions
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            )
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 0
            )
        }

        startCamera()
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun startCamera() {
        val cameraController = LifecycleCameraController(baseContext)
        val previewView: PreviewView = viewBinding.viewFinder

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        barcodeScanner = BarcodeScanning.getClient(options)

        val intent = Intent(this, AuthWidgetConfigureActivity::class.java)

        cameraController.setImageAnalysisAnalyzer(
            ContextCompat.getMainExecutor(this),
            MlKitAnalyzer(
                listOf(barcodeScanner),
                COORDINATE_SYSTEM_VIEW_REFERENCED,
                ContextCompat.getMainExecutor(this)
            ) { result: MlKitAnalyzer.Result? ->
                val barcodeResults = result?.getValue(barcodeScanner)
                if ((barcodeResults == null) ||
                    (barcodeResults.size == 0) ||
                    (barcodeResults.first() == null)
                ) {
                    return@MlKitAnalyzer
                }
                intent.putExtra("url", barcodeResults.first()!!.rawValue)
                setResult(RESULT_OK, intent)
                finish()
            }
        )

        cameraController.bindToLifecycle(this)
        previewView.controller = cameraController
    }
}
