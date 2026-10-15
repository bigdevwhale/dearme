package app.dearme.ui.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.dearme.R
import app.dearme.data.Media
import app.dearme.data.PhotoMode
import app.dearme.ui.Words
import app.dearme.ui.components.DualPhoto
import app.dearme.ui.components.PrimaryButton
import app.dearme.ui.components.rememberNow
import app.dearme.ui.theme.Hand
import app.dearme.ui.theme.Inter
import app.dearme.ui.theme.U
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** A finished shot: one or both files, and when it was taken. */
data class Shot(val back: String?, val front: String?, val takenAt: Long) {
    val files: List<String> get() = listOfNotNull(back, front)
}

private enum class Phase { PREVIEW, SHOOTING, TURN, REVIEW }

/**
 * Full-screen camera. [mode] ANY lets the user pick back, selfie or both; DUAL shoots the back camera,
 * then turns around for a selfie a second later. [deadline] shows the two-minute countdown.
 */
@Composable
fun CameraOverlay(mode: PhotoMode, deadline: Long?, onShot: (Shot) -> Unit, onClose: () -> Unit, retakes: Int = Int.MAX_VALUE) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; denied = !it }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) CameraBody(mode, deadline, onShot, onClose, retakes)
        else Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(stringResource(R.string.camera_needed), color = Color.White, fontFamily = Inter, fontSize = 17.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            if (denied) PrimaryButton(stringResource(R.string.try_again), { ask.launch(Manifest.permission.CAMERA) })
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.cancel), color = Color.White.copy(alpha = 0.7f), fontFamily = Inter, modifier = Modifier.clickable(onClick = onClose).padding(12.dp))
        }
    }
}

@Composable
private fun CameraBody(mode: PhotoMode, deadline: Long?, onShot: (Shot) -> Unit, onClose: () -> Unit, retakes: Int) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            imageCaptureMode = ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
        }
    }
    DisposableEffect(owner) {
        controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }
    var ready by remember { mutableStateOf(false) }
    var hasBack by remember { mutableStateOf(true) }
    var hasFront by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        runCatching { controller.initializationFuture.awaitDone(context) }
        hasBack = runCatching { controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)
        hasFront = runCatching { controller.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) }.getOrDefault(false)
        ready = true
    }

    var chosen by remember { mutableStateOf(if (mode == PhotoMode.ANY || mode == PhotoMode.NONE) PhotoMode.DUAL else mode) }
    // Phones with one camera shoot what they have.
    val effective = when {
        !ready -> chosen
        chosen == PhotoMode.DUAL && !hasFront -> PhotoMode.BACK
        chosen == PhotoMode.DUAL && !hasBack -> PhotoMode.FRONT
        chosen == PhotoMode.BACK && !hasBack -> PhotoMode.FRONT
        chosen == PhotoMode.FRONT && !hasFront -> PhotoMode.BACK
        else -> chosen
    }
    val wantFront = effective == PhotoMode.FRONT
    LaunchedEffect(wantFront, ready) {
        if (ready) controller.cameraSelector = if (wantFront) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
    }

    var phase by remember { mutableStateOf(Phase.PREVIEW) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var retakesLeft by remember { mutableIntStateOf(retakes) }
    var failed by remember { mutableStateOf(false) }

    fun discard() {
        shot?.let { Media.delete(context, it.files) }
        shot = null
    }
    BackHandler { if (phase == Phase.REVIEW && retakesLeft > 0) { discard(); retakesLeft--; phase = Phase.PREVIEW } else { discard(); onClose() } }

    fun shoot() {
        if (phase != Phase.PREVIEW || !ready) return
        phase = Phase.SHOOTING
        failed = false
        scope.launch {
            val takenAt = System.currentTimeMillis()
            val result = runCatching {
                when (effective) {
                    PhotoMode.FRONT -> Shot(null, controller.capture(context, front = true), takenAt)
                    PhotoMode.DUAL -> {
                        val back = controller.capture(context, front = false)
                        phase = Phase.TURN
                        controller.cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                        // Time for the selfie camera to open and settle its exposure.
                        delay(1300)
                        val front = runCatching { controller.capture(context, front = true) }.getOrNull()
                        controller.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        Shot(back, front, takenAt)
                    }
                    else -> Shot(controller.capture(context, front = false), null, takenAt)
                }
            }
            result.onSuccess { shot = it; phase = Phase.REVIEW }
                .onFailure { failed = true; phase = Phase.PREVIEW }
        }
    }

    Column(Modifier.fillMaxSize().systemBarsPadding()) {
        // Top: close, the countdown, and the mode switch.
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { discard(); onClose() }) { Icon(Icons.Rounded.Close, stringResource(R.string.close), tint = Color.White) }
            Spacer(Modifier.weight(1f))
            if (deadline != null) DeadlinePill(deadline)
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Box(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp).aspectRatio(3f / 4f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF111111)),
            contentAlignment = Alignment.Center,
        ) {
            val s = shot
            if (phase == Phase.REVIEW && s != null) {
                DualPhoto(s.back, s.front, Modifier.fillMaxSize(), corner = 22.dp)
            } else {
                AndroidView(
                    factory = { PreviewView(it).apply { this.controller = controller; scaleType = PreviewView.ScaleType.FILL_CENTER } },
                    modifier = Modifier.fillMaxSize(),
                )
            }
            androidx.compose.animation.AnimatedVisibility(visible = phase == Phase.TURN, enter = fadeIn(), exit = fadeOut()) {
                Text(stringResource(R.string.now_you), color = Color.White, fontFamily = Hand, fontSize = 46.sp, modifier = Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp)).padding(horizontal = 18.dp, vertical = 6.dp))
            }
            if (phase == Phase.SHOOTING) CircularProgressIndicator(color = Color.White)
        }
        Spacer(Modifier.height(18.dp))
        if (phase == Phase.REVIEW) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (retakesLeft > 0) {
                    Box(
                        Modifier.weight(1f).height(54.dp).clip(U.RMd).border(1.dp, Color.White.copy(alpha = 0.4f), U.RMd)
                            .clickable { discard(); retakesLeft--; phase = Phase.PREVIEW },
                        contentAlignment = Alignment.Center,
                    ) {
                        val label = if (retakes == Int.MAX_VALUE) stringResource(R.string.retake) else stringResource(R.string.retake_left, retakesLeft)
                        Text(label, color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold)
                    }
                }
                PrimaryButton(stringResource(R.string.use_photo), { shot?.let { val s = it; shot = null; onShot(s) } }, Modifier.weight(1f))
            }
        } else {
            if (mode == PhotoMode.ANY || mode == PhotoMode.NONE) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    listOf(PhotoMode.BACK, PhotoMode.DUAL, PhotoMode.FRONT).forEach { m ->
                        val on = chosen == m
                        Text(
                            Words.photoMode(context, m).orEmpty(),
                            color = if (on) Color.Black else Color.White.copy(alpha = 0.8f),
                            fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 4.dp).clip(CircleShape)
                                .background(if (on) Color.White else Color.White.copy(alpha = 0.12f))
                                .clickable(enabled = phase == Phase.PREVIEW) { chosen = m }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        )
                    }
                }
                Spacer(Modifier.height(18.dp))
            }
            if (failed) Text(stringResource(R.string.camera_failed), color = Color.White.copy(alpha = 0.8f), fontFamily = Inter, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(80.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                        .background(if (phase == Phase.PREVIEW && ready) Color.White else Color.White.copy(alpha = 0.4f))
                        .clickable(enabled = phase == Phase.PREVIEW && ready) { shoot() },
                )
            }
        }
    }
}

@Composable
private fun DeadlinePill(deadline: Long) {
    val now = rememberNow(250)
    val left = deadline - now
    val late = left <= 0
    Text(
        if (late) stringResource(R.string.late_by, Words.minSec(-left)) else Words.minSec(left),
        color = Color.White, fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 16.sp,
        modifier = Modifier.clip(CircleShape).background(if (late) Color(0xFF555555) else U.c.wax).padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

private suspend fun <T> com.google.common.util.concurrent.ListenableFuture<T>.awaitDone(context: Context) =
    suspendCancellableCoroutine { cont -> addListener({ cont.resume(Unit) }, ContextCompat.getMainExecutor(context)) }

/** Takes one picture with the current camera and saves it upright (selfies mirrored, as seen on screen). */
private suspend fun LifecycleCameraController.capture(context: Context, front: Boolean): String {
    val (bmp, rotation) = suspendCancellableCoroutine<Pair<Bitmap, Int>> { cont ->
        takePicture(ContextCompat.getMainExecutor(context), object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val r = runCatching { image.toBitmap() to image.imageInfo.rotationDegrees }
                image.close()
                r.onSuccess { cont.resume(it) }.onFailure { cont.resumeWithException(it) }
            }

            override fun onError(exception: ImageCaptureException) = cont.resumeWithException(exception)
        })
    }
    return withContext(Dispatchers.IO) { Media.saveShot(context, bmp, rotation, mirror = front) }
}
