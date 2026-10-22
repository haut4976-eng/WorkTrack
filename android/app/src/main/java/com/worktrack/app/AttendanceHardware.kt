package com.worktrack.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import android.location.Location
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.common.InputImage
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import java.io.IOException

fun FragmentActivity.authenticateAttendance(onResult: (Boolean, String) -> Unit) {
    val available = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (available != BiometricManager.BIOMETRIC_SUCCESS) {
        onResult(false, "Hãy đăng ký vân tay trong Android Settings trước khi chấm công.")
        return
    }
    val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onResult(true, "") }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { onResult(false, errString.toString()) }
        override fun onAuthenticationFailed() { /* Prompt cho phép thử lại. */ }
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
        .setTitle("Xác thực chấm công")
        .setSubtitle("Xác thực trên thiết bị để tiếp tục kiểm tra GPS và QR")
        .setNegativeButtonText("Hủy")
        .build())
}

suspend fun FragmentActivity.currentLocation(): Location? {
    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    return suspendCancellableCoroutine { continuation ->
        val source = CancellationTokenSource()
        LocationServices.getFusedLocationProviderClient(this)
            .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, source.token)
            .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
        continuation.invokeOnCancellation { source.cancel() }
    }
}

fun qrBitmap(token: String): Bitmap {
        val matrix = QRCodeWriter().encode(token, BarcodeFormat.QR_CODE, 640, 640)
        val bitmap = Bitmap.createBitmap(640, 640, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(640 * 640) { i -> if (matrix[i % 640, i / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        bitmap.setPixels(pixels, 0, 640, 0, 0, 640, 640)
        return bitmap
}

fun saveQrImage(context: Context, token: String): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "WorkTrack-QR-${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/WorkTrack")
    }
    val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Không tạo được ảnh")
    context.contentResolver.openOutputStream(uri)?.use { qrBitmap(token).compress(Bitmap.CompressFormat.PNG, 100, it) } ?: throw IOException("Không ghi được ảnh")
    true
} catch (_: Exception) { false }

fun scanQrImage(context: Context, uri: Uri, onResult: (String?) -> Unit) {
    val scanner = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
    try {
        scanner.process(InputImage.fromFilePath(context, uri))
            .addOnSuccessListener { codes -> onResult(codes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue) }
            .addOnFailureListener { onResult(null) }
            .addOnCompleteListener { scanner.close() }
    } catch (_: Exception) { scanner.close(); onResult(null) }
}

@Composable
fun QrImage(token: String) {
    val image = remember(token) { qrBitmap(token).asImageBitmap() }
    Image(image, contentDescription = "Mã QR chấm công có thời hạn", modifier = Modifier.fillMaxWidth().height(320.dp))
}

@Composable
fun QrScanner(onScanned: (String) -> Unit, onError: (String) -> Unit) {
    val activity = LocalContext.current as FragmentActivity
    val previewView = remember { PreviewView(activity) }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    DisposableEffect(activity) {
        val worker = Executors.newSingleThreadExecutor()
        val providerFuture = ProcessCameraProvider.getInstance(activity)
        val disposed = AtomicBoolean(false)
        val delivered = AtomicBoolean(false)
        providerFuture.addListener({
            if (!disposed.get()) try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(worker) { imageProxy ->
                    val media = imageProxy.image
                    if (media == null || delivered.get()) { imageProxy.close(); return@setAnalyzer }
                    val input = InputImage.fromMediaImage(media, imageProxy.imageInfo.rotationDegrees)
                    scanner.process(input)
                        .addOnSuccessListener { codes ->
                            val value = codes.firstOrNull { it.rawValue?.isNotBlank() == true }?.rawValue
                            if (value != null && delivered.compareAndSet(false, true)) onScanned(value)
                        }
                        .addOnFailureListener { onError("Không đọc được QR. Hãy đưa mã vào khung camera và thử lại.") }
                        .addOnCompleteListener { imageProxy.close() }
                }
                provider.unbindAll()
                provider.bindToLifecycle(activity, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            } catch (_: Exception) { onError("Không mở được camera. Kiểm tra quyền camera và Emulator.") }
        }, ContextCompat.getMainExecutor(activity))
        onDispose {
            disposed.set(true)
            if (providerFuture.isDone) runCatching { providerFuture.get().unbindAll() }
            scanner.close()
            worker.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxWidth().height(360.dp))
}
