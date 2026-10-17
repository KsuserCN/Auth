package cn.ksuser.auth.core.settings

data class CropRectangle(val x: Int, val y: Int, val size: Int)

object AvatarCropGeometry {
    fun square(width: Int, height: Int, zoom: Float = 1f, horizontal: Float = .5f, vertical: Float = .5f): CropRectangle {
        require(width > 0 && height > 0)
        require(zoom.isFinite() && horizontal.isFinite() && vertical.isFinite())
        val side = (minOf(width, height) / zoom.coerceIn(1f, 3f)).toInt().coerceAtLeast(1)
        return CropRectangle(((width - side) * horizontal.coerceIn(0f, 1f)).toInt(),
            ((height - side) * vertical.coerceIn(0f, 1f)).toInt(), side)
    }
}
