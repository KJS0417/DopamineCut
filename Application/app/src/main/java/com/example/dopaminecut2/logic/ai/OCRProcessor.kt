package com.example.dopaminecut2.logic.ai

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions

class OCRProcessor {

    private val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

    fun process(image: InputImage, onResult: (String) -> Unit) {
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                onResult(visionText.text)
            }
            .addOnFailureListener {
                onResult("")
            }
    }
}
