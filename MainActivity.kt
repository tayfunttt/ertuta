package it.ertuta.biometrics

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Bundle
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var cameraManager: CameraManager
    private val targetUrl = "https://" + "ertuta.it"

    private var frontCameraDevice: CameraDevice? = null
    private var backCameraDevice: CameraDevice? = null

    private var frontImageReader: ImageReader? = null
    private var backImageReader: ImageReader? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        webView.webViewClient = WebViewClient()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startSystem()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 101)
        }
    }

    private fun startSystem() {
        webView.loadUrl(targetUrl)
        startConcurrentCameras()
    }

    @SuppressLint("MissingPermission")
    private fun startConcurrentCameras() {
        val concurrentSets: Set<Set<String>> = try {
            cameraManager.concurrentCameraIds
        } catch (e: Exception) {
            emptySet()
        }

        var frontId: String? = null
        var backId: String? = null

        for (set in concurrentSets) {
            for (id in set) {
                try {
                    val chars = cameraManager.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_FRONT) frontId = id
                    if (facing == CameraCharacteristics.LENS_FACING_BACK) backId = id
                } catch (e: Exception) {
                }
            }
            if (frontId != null && backId != null) break
        }

        if (frontId == null || backId == null) {
            Toast.makeText(this, "Galaxy S eszamanli cift kamera kombinasyonu bulunamadi!", Toast.LENGTH_LONG).show()
            return
        }

        frontImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2)
        frontImageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val timestamp = System.currentTimeMillis()
            webView.post {
                webView.evaluateJavascript("if(window.onFrontFrame) window.onFrontFrame($timestamp);", null)
            }
            image.close()
        }, null)

        backImageReader = ImageReader.newInstance(320, 240, ImageFormat.YUV_420_888, 2)
        backImageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val timestamp = System.currentTimeMillis()
            webView.post {
                webView.evaluateJavascript("if(window.onBackFrame) window.onBackFrame($timestamp);", null)
            }
            image.close()
        }, null)

        try {
            cameraManager.openCamera(frontId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    frontCameraDevice = camera
                    frontImageReader?.let { createSession(camera, it) }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, null)

            cameraManager.openCamera(backId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    backCameraDevice = camera
                    backImageReader?.let { createSession(camera, it) }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close() }
                override fun onError(camera: CameraDevice, error: Int) { camera.close() }
            }, null)
        } catch (e: Exception) {
            Toast.makeText(this, "Kamera baslatilamadi: " + e.message, Toast.LENGTH_LONG).show()
        }
    }

    private fun createSession(camera: CameraDevice, reader: ImageReader) {
        val surface = reader.surface
        try {
            val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
            }
            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    try {
                        session.setRepeatingRequest(builder.build(), null, null)
                    } catch (e: Exception) {
                    }
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            }, null)
        } catch (e: Exception) {
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        frontCameraDevice?.close()
        backCameraDevice?.close()
        frontImageReader?.close()
        backImageReader?.close()
    }
}
