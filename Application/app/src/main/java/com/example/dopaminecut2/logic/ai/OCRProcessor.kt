package com.example.dopaminecut2.logic.ai

import com.example.dopaminecut2.logic.manager.NormalizedBounds
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

data class OcrTextLine(
    val text: String,
    val bounds: NormalizedBounds
)

data class OcrFrame(
    val lines: List<OcrTextLine>,
    val width: Int = 0,
    val height: Int = 0
)

class OCRProcessor {

    private val recognizer = TextRecognition.getClient(
        KoreanTextRecognizerOptions.Builder().build()
    )

    suspend fun recognizeFrame(image: InputImage): Result<OcrFrame> = try {
        val recognized = recognizer.process(image).await()
        val width = image.width.coerceAtLeast(1).toFloat()
        val height = image.height.coerceAtLeast(1).toFloat()
        val lines = recognized.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                val text = line.text.trim().takeIf(String::isNotEmpty) ?: return@mapNotNull null
                OcrTextLine(
                    text = text,
                    bounds = NormalizedBounds(
                        left = (box.left / width).coerceIn(0f, 1f),
                        top = (box.top / height).coerceIn(0f, 1f),
                        right = (box.right / width).coerceIn(0f, 1f),
                        bottom = (box.bottom / height).coerceIn(0f, 1f)
                    )
                )
            }
        }
        Result.success(OcrFrame(lines = lines, width = image.width, height = image.height))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun close() {
        recognizer.close()
    }
}
