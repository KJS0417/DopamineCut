package com.example.dopaminecut2.logic.shortform

import kotlin.math.exp

data class ShortformContentPrediction(
    val kind: ShortformContentKind,
    val confidence: Float,
    val probabilities: Map<ShortformContentKind, Float>
)

/** 낮은 신뢰도의 CV 출력은 억지로 확정하지 않고 UNKNOWN으로 보낸다. */
class ShortformContentDecisionPolicy(
    private val normalThreshold: Float = 0.65f,
    private val adThreshold: Float = 0.75f,
    private val liveThreshold: Float = 0.75f,
    private val photoThreshold: Float = 0.75f
) {
    init {
        require(listOf(normalThreshold, adThreshold, liveThreshold, photoThreshold).all { it in 0f..1f })
    }

    fun fromLogits(logits: FloatArray): ShortformContentPrediction {
        require(logits.size == MODEL_KINDS.size)
        require(logits.all(Float::isFinite)) { "CV 모델의 출력이 유한한 수가 아닙니다." }
        val maxLogit = logits.maxOrNull() ?: 0f
        val exponentials = logits.map { value -> exp((value - maxLogit).toDouble()) }
        val sum = exponentials.sum().takeIf { it > 0.0 } ?: 1.0
        val probabilities = MODEL_KINDS.mapIndexed { index, kind ->
            kind to (exponentials[index] / sum).toFloat()
        }.toMap()
        val best = probabilities.maxBy { it.value }
        val threshold = when (best.key) {
            ShortformContentKind.NORMAL -> normalThreshold
            ShortformContentKind.AD -> adThreshold
            ShortformContentKind.LIVE -> liveThreshold
            ShortformContentKind.PHOTO_POST -> photoThreshold
            ShortformContentKind.UNKNOWN -> 1f
        }
        return ShortformContentPrediction(
            kind = if (best.value >= threshold) best.key else ShortformContentKind.UNKNOWN,
            confidence = best.value,
            probabilities = probabilities
        )
    }

    private companion object {
        val MODEL_KINDS = listOf(
            ShortformContentKind.NORMAL,
            ShortformContentKind.AD,
            ShortformContentKind.LIVE,
            ShortformContentKind.PHOTO_POST
        )
    }
}
