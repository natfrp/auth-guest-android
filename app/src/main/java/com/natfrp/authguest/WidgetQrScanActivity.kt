package com.natfrp.authguest

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageAnalysis
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.core.content.ContextCompat
import com.natfrp.authguest.databinding.ActivityWidgetQrScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean


class WidgetQrScanActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityWidgetQrScanBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var cameraController: LifecycleCameraController
    private val scanning = AtomicBoolean(true)
    private val decoder by lazy(LazyThreadSafetyMode.NONE) { QrDecoder() }
    private var cameraStarted = false
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, R.string.qr_camera_permission_required, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityWidgetQrScanBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        cameraExecutor = Executors.newSingleThreadExecutor { Thread(it, "qr-decoder") }
        cameraController = LifecycleCameraController(this).apply {
            setEnabledUseCases(CameraController.IMAGE_ANALYSIS)
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            imageAnalysisTargetSize = CameraController.OutputSize(Size(1280, 720))
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionRequest.launch(Manifest.permission.CAMERA)
    }

    private fun startCamera() {
        if (cameraStarted || isFinishing || isDestroyed) return
        cameraStarted = true
        val mainExecutor = ContextCompat.getMainExecutor(this)
        cameraController.setImageAnalysisAnalyzer(cameraExecutor) { image ->
            val value = try {
                if (!scanning.get()) return@setImageAnalysisAnalyzer
                // CameraController shares the FILL_CENTER ViewPort with PreviewView.
                // Only change the crop metadata; the Y buffer is neither copied nor resized.
                image.setCropRect(qrScanRegion(image.cropRect))
                decoder.decode(image)
            } catch (e: Exception) {
                Log.w("WidgetQrScanActivity", "Could not decode camera frame", e)
                null
            } finally {
                image.close()
            }
            if (!value.isNullOrEmpty() && scanning.compareAndSet(true, false)) {
                mainExecutor.execute {
                    if (!isFinishing && !isDestroyed) {
                        setResult(RESULT_OK, Intent().putExtra("url", value))
                        finish()
                    }
                }
            }
        }
        cameraController.bindToLifecycle(this)
        viewBinding.viewFinder.controller = cameraController
    }

    override fun onDestroy() {
        scanning.set(false)
        cameraController.clearImageAnalysisAnalyzer()
        // Detach only this controller; unbind() would also stop a newly recreated Activity's camera.
        viewBinding.viewFinder.controller = null
        cameraExecutor.shutdown()
        super.onDestroy()
    }
}
