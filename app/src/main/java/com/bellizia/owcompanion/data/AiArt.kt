package com.bellizia.owcompanion.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.bellizia.owcompanion.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Pictures for a comic panel, generated on demand by a free service.
 *
 * Two kinds, and they behave very differently, which is the single most important thing to
 * know before using this: **scenes come out well nearly every time; figures come out well
 * about one time in three**. Measured, not guessed. Six figure prompts under identical
 * conditions gave two usable silhouettes; the failures were a second person wandering into
 * frame, a grey panel behind the subject, and a shape that read as nothing at all. So the
 * workshop asks for several and lets a person choose, rather than pretending the first
 * answer is good - and says out loud how many survived.
 *
 * ### One at a time, not all at once
 *
 * The obvious implementation asks for six in parallel and it does not work: the service
 * allows one request at a time and answers 429 to the rest within a fifth of a second. Six
 * parallel gave one picture; six in sequence gave six. So the workshop queues them, shows
 * each as it lands, and a reader can take the first one without waiting for the others.
 *
 * ### Why the prompts are not writable
 *
 * Every prompt is assembled here from a closed vocabulary. Nothing a reader types reaches
 * the generator. That is a safety property first - an app that pipes free text into an image
 * model is an app that generates whatever somebody asks it to, under this app's name - and a
 * quality property second, since these particular phrasings are the ones that survived
 * testing.
 *
 * ### Why no hero is ever named
 *
 * The prompts describe anonymous figures and empty places, never an Overwatch character or a
 * named map. Asking a model to draw somebody else's copyrighted character is precisely what
 * this project has avoided everywhere else - the tactics board makes readers bring their own
 * map images for the same reason - and a machine's guess at a hero's likeness would be both
 * legally worse and visibly worse than the official portrait the app already has.
 */
object AiArt {

    /** A pose for a figure. Names are keys; the wording sent is [prompt]. */
    enum class Pose(private val phrase: String) {
        Running("running forward"),
        Aiming("aiming a weapon"),
        Standing("standing still, arms at their sides"),
        Leaping("leaping through the air"),
        Crouching("crouching low"),
        Cheering("both arms raised in celebration"),
        Fallen("kneeling on the ground, defeated"),
        Pointing("pointing forward with one arm");

        val prompt: String
            get() = "one single black silhouette of a person $phrase, alone on an entirely " +
                "plain white background, whole body, nothing else in the frame, " +
                "no second figure, no wall, no panel, no floor, no shadow, no text, " +
                "flat black shape, vector icon"
    }

    /** A place for a panel's background, in the game's visual language but of nowhere real. */
    enum class Scene(private val phrase: String) {
        Street("a wide empty city street with tram tracks down the middle"),
        Courtyard("an empty sunlit courtyard with fountains and archways"),
        Rooftop("an empty rooftop above a bright skyline at golden hour"),
        Factory("an empty industrial hall with catwalks and orange machinery"),
        Temple("an empty stone temple corridor with tall red pillars"),
        Snow("an empty snowbound village square with wooden houses"),
        Desert("an empty desert outpost with concrete walls and satellite dishes"),
        Lab("an empty white laboratory hall with glass panels and blue light");

        val prompt: String
            get() = "$phrase, stylised bright cartoon video game environment art, " +
                "clean shapes, wide establishing shot, no people, no characters, " +
                "no logos, no text, no watermark"
    }

    /**
     * A generated picture, cached on disk under a name derived from what made it.
     *
     * Same pose and same seed means the same file, so flicking back and forth through the
     * candidates costs one download each rather than one per look.
     */
    suspend fun fetch(context: Context, prompt: String, seed: Int, wide: Boolean): File? =
        withContext(Dispatchers.IO) {
            val cache = File(context.cacheDir, "ai").apply { mkdirs() }
            val file = File(cache, "${prompt.hashCode()}-$seed-${if (wide) "w" else "s"}.jpg")
            if (file.exists() && file.length() > 0) return@withContext file

            runCatching {
                val width = if (wide) 768 else 512
                val height = 512
                val url = URL(
                    "$HOST/prompt/${URLEncoder.encode(prompt, "UTF-8")}" +
                        "?width=$width&height=$height&nologo=true&seed=$seed&model=$MODEL",
                )
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15_000
                    readTimeout = 60_000
                    setRequestProperty("User-Agent", "OwCompanion/${BuildConfig.VERSION_NAME}")
                }
                val bytes = try {
                    val code = connection.responseCode
                    // Too many at once is a queue, not a refusal: the service answers 429
                    // the moment a second request overlaps the first. The caller waits and
                    // comes back rather than reporting a picture that failed.
                    if (code == TOO_MANY) throw Busy()
                    if (code !in 200..299) return@runCatching null
                    connection.inputStream.use { it.readBytesCapped(MAX_IMAGE_BYTES) }
                } finally {
                    connection.disconnect()
                }
                // Decoded before it is kept: a body that is not an image, or is truncated,
                // should fail here rather than become a blank rectangle in somebody's strip.
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    ?: return@runCatching null
                file.writeBytes(bytes)
                file
            }.getOrElse { error -> if (error is Busy) throw error else null }
        }

    /** Thrown when the service is asking to be given a moment. */
    class Busy : RuntimeException()

    fun decode(file: File): Bitmap? = runCatching {
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    /**
     * Where the pictures come from, for the credit the About screen owes it.
     *
     * Named in one place so the screen and any future change agree.
     */
    const val CREDIT = "pollinations.ai"

    private const val HOST = "https://image.pollinations.ai"

    /**
     * The fast model.
     *
     * Measured against the same prompt: turbo answers in about fifteen seconds where the
     * default and flux both take about forty-five. Three times the wait buys quality nobody
     * can see in a silhouette that is about to be reduced to one flat colour anyway.
     */
    private const val MODEL = "turbo"

    private const val TOO_MANY = 429

    /** A 768x512 JPEG is tens of kilobytes; a megabyte is already far past generous. */
    private const val MAX_IMAGE_BYTES = 4_000_000
}
