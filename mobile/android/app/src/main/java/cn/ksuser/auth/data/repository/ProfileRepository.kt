package cn.ksuser.auth.data.repository

import android.content.ContentResolver
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import cn.ksuser.auth.core.network.KsuserApiService
import cn.ksuser.auth.core.network.executeEnvelope
import cn.ksuser.auth.core.network.requireCode
import cn.ksuser.auth.data.model.UpdateProfileRequest
import cn.ksuser.auth.data.model.UserProfile
import cn.ksuser.auth.core.settings.AvatarCropGeometry
import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository(
    private val api: KsuserApiService,
    private val gson: Gson,
) {
    suspend fun getProfile(details: Boolean = true): UserProfile {
        val envelope = executeEnvelope(gson) { api.getUserInfo(if (details) "details" else "basic") }
        requireCode(envelope, 200)
        return envelope.data ?: error("用户信息为空")
    }

    suspend fun updateField(
        key: String,
        value: String,
    ): UserProfile {
        val envelope = executeEnvelope(gson) { api.updateProfile(UpdateProfileRequest(key, value)) }
        requireCode(envelope, 200)
        // Mutation responses are intentionally compact. Never replace the complete account snapshot with them.
        return getProfile()
    }

    suspend fun uploadAvatar(
        contentResolver: ContentResolver,
        uri: Uri,
    ): UserProfile {
        return uploadAvatar(prepareAvatar(contentResolver, uri))
    }

    suspend fun prepareAvatar(contentResolver: ContentResolver, uri: Uri): Bitmap = withContext(Dispatchers.IO) {
        val maxBytes = 10 * 1024 * 1024
        val bytes = contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= maxBytes) { "头像图片不能超过 10 MB" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("无法读取头像文件")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "请选择有效的图片" }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: error("无法读取头像图片")
        val orientation = runCatching { ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1) }.getOrDefault(1)
        val matrix = Matrix().apply {
            when (orientation) {
                2 -> setScale(-1f, 1f)
                3 -> setRotate(180f)
                4 -> setScale(1f, -1f)
                5 -> { setRotate(90f); postScale(-1f, 1f) }
                6 -> setRotate(90f)
                7 -> { setRotate(270f); postScale(-1f, 1f) }
                8 -> setRotate(270f)
            }
        }
        if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also { if (it !== bitmap) bitmap.recycle() }
    }

    suspend fun uploadAvatar(bitmap: Bitmap): UserProfile {
        val bytes = withContext(Dispatchers.Default) {
            val rect = AvatarCropGeometry.square(bitmap.width, bitmap.height)
            val square = Bitmap.createBitmap(bitmap, rect.x, rect.y, rect.size, rect.size)
            val dimension = minOf(1024, square.width)
            val scaled = Bitmap.createScaledBitmap(square, dimension, dimension, true)
            ByteArrayOutputStream().use { output ->
                check(scaled.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "头像压缩失败" }
                if (scaled !== bitmap) scaled.recycle()
                if (square !== bitmap && square !== scaled) square.recycle()
                output.toByteArray()
            }
        }
        require(bytes.size <= 10 * 1024 * 1024) { "头像图片不能超过 10 MB" }
        val requestBody = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = MultipartBody.Part.createFormData("file", "avatar.jpg", requestBody)
        val envelope = executeEnvelope(gson) { api.uploadAvatar(part) }
        requireCode(envelope, 200)
        return getProfile()
    }
}
