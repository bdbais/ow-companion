package com.bellizia.owcompanion.data

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Asking for a rating, when there is somewhere for one to go.
 *
 * Google's in-app review card is the only way to take stars and a comment without leaving
 * the app, and it comes with conditions worth stating rather than discovering:
 *
 *  - It works only for a copy installed from Play. Side-loaded - which is every build so
 *    far - it does nothing at all, so the app has to know the difference and offer the
 *    listing instead.
 *  - Play decides whether the card actually appears. It is quota-limited and gives no way
 *    to find out whether anyone rated, by design.
 *  - It must not be gated on liking the app. Asking only the happy ones is against Play's
 *    policy, so this never inspects anything before offering.
 *
 * Nothing here collects an opinion itself. A rating goes to Google, a bug goes to the issue
 * tracker, and this app keeps neither.
 */
class Feedback(private val context: Context) {

    /** Whether there is a listing to rate on at all, which side-loaded there is not. */
    val published: Boolean get() = fromPlay()

    /**
     * Whether this copy came from Play.
     *
     * The installer package is the only honest signal, and it moved in API 30: the old call
     * still works but is deprecated, so both are kept rather than losing the answer on one
     * side or the other.
     */
    private fun fromPlay(): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName)
        }
        installer in PLAY_INSTALLERS
    }.getOrDefault(false)

    /**
     * Shows Google's card, and reports whether it got as far as being shown.
     *
     * A false here is not a failure worth reporting to anyone: Play refuses far more often
     * than it accepts, and the caller's job is simply to offer the listing instead.
     */
    suspend fun requestReview(activity: Activity): Boolean {
        if (!fromPlay()) return false
        return runCatching {
            val manager = ReviewManagerFactory.create(context)
            val info = suspendCancellableCoroutine<ReviewInfo?> { continuation ->
                manager.requestReviewFlow()
                    .addOnCompleteListener { task ->
                        continuation.resume(task.takeIf { it.isSuccessful }?.result)
                    }
            } ?: return false
            suspendCancellableCoroutine { continuation ->
                manager.launchReviewFlow(activity, info)
                    .addOnCompleteListener { continuation.resume(true) }
            }
        }.getOrDefault(false)
    }

    /**
     * Opens the store listing, where stars and a written review both live.
     *
     * `market://` hands straight to the Play app; the https address is the same page for a
     * device without it. Until the app is actually published, neither exists, so the caller
     * should be sending people to the issue tracker instead.
     */
    fun openListing() {
        val play = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse(LISTING + context.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(play) }
            .recoverCatching { context.startActivity(web) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    /** Where a bug belongs while there is no listing, and afterwards too. */
    fun openIssues() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(ISSUES))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { if (it !is ActivityNotFoundException) throw it }
    }

    private companion object {
        /** Play itself, and the package the store runs under on some devices. */
        val PLAY_INSTALLERS = setOf("com.android.vending", "com.google.android.feedback")

        const val LISTING = "https://play.google.com/store/apps/details?id="
        const val ISSUES = "https://github.com/bdbais/ow-companion/issues"
    }
}
