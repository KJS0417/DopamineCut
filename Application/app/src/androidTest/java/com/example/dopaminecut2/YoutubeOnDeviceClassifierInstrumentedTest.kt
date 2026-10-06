package com.example.dopaminecut2

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.dopaminecut2.logic.shortform.ShortformContentKind
import com.example.dopaminecut2.logic.shortform.YoutubeOnDeviceContentClassifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.json.JSONObject
import java.io.File

@RunWith(AndroidJUnit4::class)
class YoutubeOnDeviceClassifierInstrumentedTest {
    @Test
    fun capturedFramesMatchPythonReferenceOnDevice() = runBlocking {
        val directory = InstrumentationRegistry.getArguments().getString("cvFixtureDirectory")
        assumeTrue("Optional local diagnostic fixtures are not supplied", directory != null)
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val classifier = YoutubeOnDeviceContentClassifier(context)
        try {
            val reference = JSONObject(File(directory, "cv-reference.json").readText())
            val assetMetadata = JSONObject(context.assets.open("models/youtube_shortform_state.json").bufferedReader().use { it.readText() })
            assertEquals(assetMetadata.getString("sha256"), reference.getString("model_sha256"))
            val frames = reference.getJSONArray("frames")
            for (index in 0 until frames.length()) {
                val entry = frames.getJSONObject(index)
                val file = entry.getString("file")
                val bitmap = requireNotNull(BitmapFactory.decodeFile("$directory/$file"))
                try {
                    val started = android.os.SystemClock.elapsedRealtime()
                    val prediction = classifier.classify(bitmap).getOrThrow()
                    Log.i("CvParity", "$file probabilities=${prediction.probabilities} " +
                        "latencyMs=${android.os.SystemClock.elapsedRealtime() - started}")
                    val expected = entry.getJSONObject("probabilities")
                    prediction.probabilities.forEach { (kind, probability) ->
                        assertEquals(expected.getDouble(kind.name).toFloat(), probability, 0.001f)
                    }
                    assertEquals(entry.getString("kind"), prediction.kind.name)
                } finally {
                    bitmap.recycle()
                }
            }
        } finally {
            classifier.close()
        }
    }

    @Test
    fun modelAssetLoadsAndRunsOnAndroid() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val classifier = YoutubeOnDeviceContentClassifier(context)

        try {
            val prediction = classifier.classify(bitmap).getOrThrow()

            assertEquals(
                setOf(
                    ShortformContentKind.NORMAL,
                    ShortformContentKind.AD,
                    ShortformContentKind.LIVE,
                    ShortformContentKind.PHOTO_POST
                ),
                prediction.probabilities.keys
            )
            assertTrue(prediction.confidence in 0f..1f)
            assertTrue(prediction.probabilities.values.sum() in 0.999f..1.001f)
        } finally {
            classifier.close()
            bitmap.recycle()
        }
    }
}
