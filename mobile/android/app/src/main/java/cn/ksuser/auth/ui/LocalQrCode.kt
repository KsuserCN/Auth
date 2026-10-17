package cn.ksuser.auth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import cn.ksuser.auth.core.settings.L10n
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Recovery credentials and otpauth URIs must never be sent to a remote image service. */
@Composable
internal fun LocalQrCode(text: String, contentDescription: String, modifier: Modifier = Modifier) {
    val bitmap by produceState<Bitmap?>(null, text) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, 640, 640,
                    mapOf(EncodeHintType.MARGIN to 4, EncodeHintType.CHARACTER_SET to "UTF-8"))
                val pixels = IntArray(matrix.width * matrix.height) { index ->
                    if (matrix[index % matrix.width, index / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
                }
                Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
            }.getOrNull()
        }
    }
    bitmap?.let { Image(it.asImageBitmap(), contentDescription, modifier) }
        ?: Text(L10n.text("正在生成二维码…"))
}
