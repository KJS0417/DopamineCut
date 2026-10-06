package com.example.dopaminecut2.logic.shortform

import kotlin.math.roundToInt

/** Python whole_ui_v1과 같은 crop/fit/검은 패딩. 화면 종류와 접근성 좌표는 사용하지 않는다. */
object WholeUiTiles {
    val sources = listOf(listOf(0.0, 0.0, 1.0, 1.0), listOf(0.0, .60, 1.0, .85), listOf(0.0, .82, 1.0, .97))
    val destinations = listOf(listOf(0, 0, 160, 320), listOf(160, 0, 320, 160), listOf(160, 160, 320, 320))

    fun resize(pixels: IntArray, width: Int, height: Int): IntArray {
        require(width > 0 && height > 0 && pixels.size == width * height)
        val canvas = IntArray(320 * 320) { 0xff000000.toInt() }
        sources.zip(destinations).forEach { (source, destination) ->
            val left = (width * source[0]).roundToInt()
            val top = (height * source[1]).roundToInt()
            val right = (width * source[2]).roundToInt()
            val bottom = (height * source[3]).roundToInt()
            val cropWidth = right - left
            val cropHeight = bottom - top
            require(cropWidth > 0 && cropHeight > 0)
            val cropped = IntArray(cropWidth * cropHeight)
            for (row in 0 until cropHeight) pixels.copyInto(cropped, row * cropWidth,
                (top + row) * width + left, (top + row) * width + right)
            val targetWidth = destination[2] - destination[0]
            val targetHeight = destination[3] - destination[1]
            val scale = minOf(targetWidth.toDouble() / cropWidth, targetHeight.toDouble() / cropHeight)
            val fittedWidth = (cropWidth * scale).roundToInt().coerceIn(1, targetWidth)
            val fittedHeight = (cropHeight * scale).roundToInt().coerceIn(1, targetHeight)
            val resized = AntialiasedBilinearResize.resize(cropped, cropWidth, cropHeight, fittedWidth, fittedHeight)
            val x = destination[0] + (targetWidth - fittedWidth) / 2
            val y = destination[1] + (targetHeight - fittedHeight) / 2
            for (row in 0 until fittedHeight) resized.copyInto(canvas, (y + row) * 320 + x,
                row * fittedWidth, (row + 1) * fittedWidth)
        }
        return canvas
    }
}
