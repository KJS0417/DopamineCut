package com.example.dopaminecut2.logic.shortform

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.nio.FloatBuffer
import java.security.MessageDigest
import org.json.JSONObject

/** MobileNetV3 ONNX로 Shorts 내부 콘텐츠를 일반/광고/라이브/사진 게시물로 분류한다. */
class YoutubeOnDeviceContentClassifier(
    context: Context,
    decisionPolicy: ShortformContentDecisionPolicy? = null
) : Closeable {
    private val environment = OrtEnvironment.getEnvironment()
    private val session: OrtSession
    private val decisionPolicy: ShortformContentDecisionPolicy
    private val sessionLock = Any()
    private var closed = false

    init {
        val modelBytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
        val metadata = context.assets.open(METADATA_ASSET).bufferedReader().use {
            JSONObject(it.readText())
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(modelBytes)
            .joinToString("") { "%02x".format(it) }
        require(hash == metadata.getString("sha256")) { "YouTube 모델 체크섬이 다릅니다." }
        val labels = metadata.getJSONArray("labels")
        require((0 until labels.length()).map(labels::getString) ==
            listOf("SHORTFORM_ACTIVE", "SHORTFORM_AD", "SHORTFORM_LIVE", "PHOTO_POST"))
        require(metadata.getString("input_name") == INPUT_NAME)
        val shape = metadata.getJSONArray("input_shape")
        require((0 until shape.length()).map(shape::getInt) == listOf(1, 3, INPUT_SIZE, INPUT_SIZE))
        val preprocessing = metadata.getJSONObject("preprocessing")
        require(preprocessing.getString("ui_view_mode") == "ui_tiles_v1") { "최신 ui_tiles_v1 CV 입력만 지원합니다." }
        require(preprocessing.getString("layout") == "NCHW" && preprocessing.getString("color") == "RGB")
        for ((key, expected) in listOf("imagenet_mean" to listOf(RED_MEAN, GREEN_MEAN, BLUE_MEAN),
            "imagenet_std" to listOf(RED_STD, GREEN_STD, BLUE_STD))) {
            val values = preprocessing.getJSONArray(key)
            require(values.length() == 3 && expected.indices.all { kotlin.math.abs(values.getDouble(it) - expected[it]) < 1e-6 })
        }
        run {
            require(preprocessing.getString("experiment") == "whole_ui_v1")
            require(preprocessing.getString("tile_resize") == "fit_bilinear")
            val padding = preprocessing.getJSONArray("tile_padding_rgb")
            require(padding.length() == 3 && (0..2).all { padding.getInt(it) == 0 })
            val tiles = preprocessing.getJSONArray("roi_tiles")
            require(tiles.length() == WholeUiTiles.sources.size)
            for (i in 0 until tiles.length()) {
                val source = tiles.getJSONObject(i).getJSONArray("source_ratio")
                val destination = tiles.getJSONObject(i).getJSONArray("destination_px")
                require(source.length() == 4 && destination.length() == 4)
                require((0..3).all { kotlin.math.abs(source.getDouble(it) - WholeUiTiles.sources[i][it]) < 1e-9 })
                require((0..3).all { destination.getInt(it) == WholeUiTiles.destinations[i][it] })
            }
        }
        val thresholds = metadata.getJSONObject("decision_thresholds")
        this.decisionPolicy = decisionPolicy ?: ShortformContentDecisionPolicy(
            normalThreshold = thresholds.getDouble("SHORTFORM_ACTIVE").toFloat(),
            adThreshold = thresholds.getDouble("SHORTFORM_AD").toFloat(),
            liveThreshold = thresholds.getDouble("SHORTFORM_LIVE").toFloat(),
            photoThreshold = thresholds.getDouble("PHOTO_POST").toFloat()
        )
        session = OrtSession.SessionOptions().use { options ->
            options.setIntraOpNumThreads(2)
            environment.createSession(modelBytes, options)
        }
        try {
            val inputInfo = session.inputInfo[INPUT_NAME]?.info as? TensorInfo
            require(inputInfo?.shape?.contentEquals(longArrayOf(1, 3, 320, 320)) == true)
            val outputInfo = session.outputInfo["logits"]?.info as? TensorInfo
            require(outputInfo?.shape?.contentEquals(longArrayOf(1, 4)) == true)
        } catch (error: Exception) {
            session.close()
            throw error
        }
    }

    suspend fun classify(bitmap: Bitmap): Result<ShortformContentPrediction> =
        withContext(Dispatchers.Default) {
            try {
                Result.success(synchronized(sessionLock) {
                    check(!closed) { "YouTube 모델이 종료되었습니다." }
                    require(bitmap.height > bitmap.width) { "세로 Shorts 화면만 지원합니다." }
                    val input = preprocess(bitmap)
                    OnnxTensor.createTensor(
                        environment,
                        FloatBuffer.wrap(input),
                        longArrayOf(1L, 3L, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
                    ).use { tensor ->
                        session.run(mapOf(INPUT_NAME to tensor)).use { outputs ->
                            @Suppress("UNCHECKED_CAST")
                            val logits = outputs[0].value as Array<FloatArray>
                            this@YoutubeOnDeviceContentClassifier.decisionPolicy.fromLogits(logits.single())
                        }
                    }
                })
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    override fun close() {
        synchronized(sessionLock) {
            if (!closed) {
                session.close()
                closed = true
            }
        }
    }

    private fun preprocess(source: Bitmap): FloatArray {
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        return normalizePixels(WholeUiTiles.resize(pixels, source.width, source.height))
    }
    private fun normalizePixels(pixels: IntArray): FloatArray {
        val planeSize = pixels.size
        return FloatArray(planeSize * 3).also { output ->
            pixels.forEachIndexed { index, color ->
                output[index] = (((color shr 16) and 0xFF) / 255f - RED_MEAN) / RED_STD
                output[planeSize + index] = (((color shr 8) and 0xFF) / 255f - GREEN_MEAN) / GREEN_STD
                output[planeSize * 2 + index] = ((color and 0xFF) / 255f - BLUE_MEAN) / BLUE_STD
            }
        }
    }

    private companion object {
        const val MODEL_ASSET = "models/youtube_shortform_state.onnx"
        const val METADATA_ASSET = "models/youtube_shortform_state.json"
        const val INPUT_NAME = "screen"
        const val INPUT_SIZE = 320
        const val RED_MEAN = 0.485f
        const val GREEN_MEAN = 0.456f
        const val BLUE_MEAN = 0.406f
        const val RED_STD = 0.229f
        const val GREEN_STD = 0.224f
        const val BLUE_STD = 0.225f
    }
}
