package com.besklar.relatives.ui.portrait

import org.junit.Assert.*
import org.junit.Test

class PhotoTransformTest {
    @Test fun zoomLimitsAndReturningToFitRecenterImage() {
        val large = PhotoTransform().gesture(100f, 5000f, -5000f, 0f, 0f, 1000f, 800f, 1000f, 800f)
        assertEquals(PhotoTransform(4f, 1500f, -1200f), large)
        assertEquals(PhotoTransform(), large.gesture(0.01f, 200f, 300f, 100f, -50f, 1000f, 800f, 1000f, 800f))
    }
    @Test fun zoomPreservesFocalPointAndAppliesPan() {
        val next = PhotoTransform(2f, 50f, -60f).gesture(2f, 30f, -20f, 100f, -70f, 1000f, 800f, 1000f, 800f)
        assertEquals(4f, next.scale, 0.001f)
        assertEquals(30f, next.x, 0.001f)
        assertEquals(-70f, next.y, 0.001f)
    }
    @Test fun letterboxedAxisCannotPanUntilImageFillsViewport() {
        val next = PhotoTransform().gesture(1.5f, 5000f, 5000f, 0f, 0f, 1000f, 1000f, 1000f, 500f)
        assertEquals(PhotoTransform(1.5f, 250f, 0f), next)
        val taller = next.gesture(2f, 0f, 5000f, 0f, 0f, 1000f, 1000f, 1000f, 500f)
        assertEquals(250f, taller.y, 0.001f)
    }
    @Test fun fitIgnoresPanningAndInvalidGestureValues() {
        assertEquals(PhotoTransform(), PhotoTransform().gesture(Float.NaN, Float.NaN, 500f,
            0f, 0f, 1000f, 800f, 1000f, 800f))
        assertEquals(PhotoTransform(), PhotoTransform().gesture(-2f, 0f, 0f, 0f, 0f, 1000f, 800f, 1000f, 800f))
    }
    @Test fun unavailableViewportDoesNotCreateInvalidOffsets() {
        assertEquals(PhotoTransform(), PhotoTransform(3f, 100f, 200f).bounded(0f, 0f, 100f, 100f))
    }
}
