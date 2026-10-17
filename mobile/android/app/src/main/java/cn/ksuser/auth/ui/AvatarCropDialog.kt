package cn.ksuser.auth.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import cn.ksuser.auth.core.settings.L10n
import cn.ksuser.auth.core.settings.AvatarCropGeometry
import cn.ksuser.auth.ui.components.LoadingTextButton

/** Cropping is entirely local; only the final JPEG leaves the device. */
@Composable
internal fun AvatarCropDialog(bitmap: Bitmap, onDismiss: () -> Unit, onSave: suspend (Bitmap) -> Unit) {
    var zoom by remember { mutableStateOf(1f) }
    var horizontal by remember { mutableStateOf(0.5f) }
    var vertical by remember { mutableStateOf(0.5f) }
    val crop = remember(bitmap, zoom, horizontal, vertical) {
        val rect = AvatarCropGeometry.square(bitmap.width, bitmap.height, zoom, horizontal, vertical)
        Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.size, rect.size)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L10n.text("裁剪头像")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(crop.asImageBitmap(), L10n.text("头像裁剪预览"), Modifier.size(220.dp))
                Text(L10n.text("缩放")); Slider(zoom, { zoom = it }, valueRange = 1f..3f, modifier = Modifier.fillMaxWidth())
                Text(L10n.text("水平位置")); Slider(horizontal, { horizontal = it })
                Text(L10n.text("垂直位置")); Slider(vertical, { vertical = it })
                Text(L10n.text("头像将压缩为 JPEG，图片最大 10 MB"))
            }
        },
        confirmButton = { LoadingTextButton(L10n.text("保存"), onClick = { onSave(crop) }) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(L10n.text("取消")) } },
    )
}
