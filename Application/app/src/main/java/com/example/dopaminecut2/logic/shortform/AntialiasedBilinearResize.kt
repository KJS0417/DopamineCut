package com.example.dopaminecut2.logic.shortform

import kotlin.math.abs
import kotlin.math.floor

/** Pillow RGB bilinear와 같은 축소 필터: 넓어진 삼각 필터, 22bit 계수, 축별 8bit 반올림. */
object AntialiasedBilinearResize {
    fun resize(pixels: IntArray, width: Int, height: Int, outputSize: Int): IntArray {
        return resize(pixels, width, height, outputSize, outputSize)
    }

    fun resize(pixels: IntArray, width: Int, height: Int, outputWidth: Int, outputHeight: Int): IntArray {
        require(width > 0 && height > 0 && outputWidth > 0 && outputHeight > 0)
        require(pixels.size == width * height)
        val horizontal = weights(width, outputWidth)
        val vertical = weights(height, outputHeight)
        val intermediate = IntArray(outputWidth * height)
        for (y in 0 until height) {
            for (x in 0 until outputWidth) {
                intermediate[y * outputWidth + x] = interpolate(
                    pixels, y * width + horizontal[x].start, 1, horizontal[x].coefficients
                )
            }
        }
        return IntArray(outputWidth * outputHeight).also { output ->
            for (y in 0 until outputHeight) {
                for (x in 0 until outputWidth) {
                    output[y * outputWidth + x] = interpolate(
                        intermediate, vertical[y].start * outputWidth + x,
                        outputWidth, vertical[y].coefficients
                    )
                }
            }
        }
    }

    private data class Weights(val start: Int, val coefficients: IntArray)

    private fun weights(inputSize: Int, outputSize: Int): List<Weights> {
        val scale = inputSize.toDouble() / outputSize
        val filterScale = maxOf(1.0, scale)
        return List(outputSize) { index ->
            val center = (index + 0.5) * scale
            val start = floor(center - filterScale + 0.5).toInt().coerceAtLeast(0)
            val end = floor(center + filterScale + 0.5).toInt().coerceAtMost(inputSize)
            val coefficients = DoubleArray(end - start) { offset ->
                maxOf(0.0, 1.0 - abs((offset + start - center + 0.5) / filterScale))
            }
            val total = coefficients.sum()
            Weights(start, IntArray(coefficients.size) { offset ->
                floor(coefficients[offset] / total * ONE + 0.5).toInt()
            })
        }
    }

    private fun interpolate(pixels: IntArray, start: Int, stride: Int, weights: IntArray): Int {
        var red = HALF
        var green = HALF
        var blue = HALF
        weights.forEachIndexed { index, coefficient ->
            val pixel = pixels[start + index * stride]
            red += ((pixel ushr 16) and 255) * coefficient
            green += ((pixel ushr 8) and 255) * coefficient
            blue += (pixel and 255) * coefficient
        }
        return (255 shl 24) or
            ((red ushr BITS).coerceIn(0, 255) shl 16) or
            ((green ushr BITS).coerceIn(0, 255) shl 8) or
            (blue ushr BITS).coerceIn(0, 255)
    }

    private const val BITS = 22
    private const val ONE = 1 shl BITS
    private const val HALF = ONE / 2
}
