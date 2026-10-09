package com.besklar.relatives.ui.portrait

import kotlin.math.max

data class PhotoTransform(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    fun gesture(zoom: Float, panX: Float, panY: Float, focalX: Float, focalY: Float,
        viewportWidth: Float, viewportHeight: Float, imageWidth: Float, imageHeight: Float): PhotoTransform {
        val change = zoom.takeIf { it.isFinite() && it > 0f } ?: 1f
        val nextScale = (scale * change).coerceIn(1f, 4f)
        val ratio = nextScale / scale
        return PhotoTransform(nextScale,
            (x - focalX) * ratio + focalX + panX,
            (y - focalY) * ratio + focalY + panY)
            .bounded(viewportWidth, viewportHeight, imageWidth, imageHeight)
    }

    fun bounded(viewportWidth: Float, viewportHeight: Float, imageWidth: Float, imageHeight: Float): PhotoTransform {
        if (viewportWidth <= 0f || viewportHeight <= 0f || imageWidth <= 0f || imageHeight <= 0f) return PhotoTransform()
        val zoom = scale.coerceIn(1f, 4f)
        if (zoom == 1f) return PhotoTransform()
        val maxX = max(0f, (imageWidth * zoom - viewportWidth) / 2f)
        val maxY = max(0f, (imageHeight * zoom - viewportHeight) / 2f)
        return PhotoTransform(zoom, x.takeIf(Float::isFinite)?.coerceIn(-maxX, maxX) ?: 0f,
            y.takeIf(Float::isFinite)?.coerceIn(-maxY, maxY) ?: 0f)
    }
}
