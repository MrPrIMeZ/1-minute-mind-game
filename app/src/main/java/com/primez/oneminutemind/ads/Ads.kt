package com.primez.oneminutemind.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.primez.oneminutemind.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Everything to do with ads:
 *  - asks for consent first (required in the EU/UK) using Google's UMP form
 *  - banner on menu screens only, never during a game
 *  - interstitial after every 2nd practice game, at most once every 90 seconds
 *  - rewarded ads are always the player's choice (+15 s, double XP, save a streak)
 */
object Ads {
    private const val TAG = "Ads"
    private const val INTERSTITIAL_EVERY = 2
    private const val INTERSTITIAL_GAP_MS = 90_000L

    private val started = AtomicBoolean(false)
    private lateinit var consent: ConsentInformation

    var ready by mutableStateOf(false)
        private set
    var rewardedReady by mutableStateOf(false)
        private set
    var privacyOptionsRequired by mutableStateOf(false)
        private set

    /** Premium: no banners or interstitials, and rewards don't need an ad. */
    var premium by mutableStateOf(false)

    /** Whether a reward button can be offered right now. */
    val canReward: Boolean get() = premium || rewardedReady

    /** Button text for a reward: "🎬 Watch an ad: +15 seconds" or "👑 +15 seconds (Premium)". */
    fun rewardLabel(what: String) = if (premium) "👑  $what (free with Premium)" else "🎬  Watch an ad: $what"

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var practiceGamesSinceAd = 0
    private var lastInterstitialAt = 0L

    /** Call from MainActivity.onCreate. Shows the consent form when needed, then starts ads. */
    fun start(activity: Activity) {
        consent = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        consent.requestConsentInfoUpdate(
            activity, params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { error ->
                    if (error != null) Log.w(TAG, "Consent form: ${error.message}")
                    privacyOptionsRequired = consent.privacyOptionsRequirementStatus ==
                        ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                    if (consent.canRequestAds()) initAds(activity)
                }
            },
            { error -> Log.w(TAG, "Consent update failed: ${error.message}") },
        )
        // Consent from an earlier launch is enough to start straight away.
        if (consent.canRequestAds()) initAds(activity)
    }

    /** "Privacy settings" button in Settings (only shown when the user's region needs it). */
    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (error != null) Log.w(TAG, "Privacy options: ${error.message}")
        }
    }

    private fun initAds(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        MobileAds.initialize(app) {
            ready = true
            loadInterstitial(app)
            loadRewarded(app)
        }
    }

    private fun request() = AdRequest.Builder().build()

    private fun loadInterstitial(context: Context) {
        InterstitialAd.load(context, BuildConfig.ADMOB_INTERSTITIAL_ID, request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) { interstitial = ad }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    Log.w(TAG, "Interstitial failed: ${error.message}")
                }
            })
    }

    private fun loadRewarded(context: Context) {
        RewardedAd.load(context, BuildConfig.ADMOB_REWARDED_ID, request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) { rewarded = ad; rewardedReady = true }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null; rewardedReady = false
                    Log.w(TAG, "Rewarded failed: ${error.message}")
                }
            })
    }

    /**
     * Call after a practice game ends, before showing the result. [then] always runs,
     * straight away if no ad is shown or after the ad is closed.
     */
    fun maybeShowInterstitial(activity: Activity?, then: () -> Unit) {
        if (premium) { then(); return }
        practiceGamesSinceAd++
        val ad = interstitial
        val now = SystemClock.elapsedRealtime()
        val due = practiceGamesSinceAd >= INTERSTITIAL_EVERY &&
            (lastInterstitialAt == 0L || now - lastInterstitialAt >= INTERSTITIAL_GAP_MS)
        if (activity == null || ad == null || !due) { then(); return }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null; loadInterstitial(activity); then()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitial = null; loadInterstitial(activity); then()
            }
        }
        practiceGamesSinceAd = 0
        lastInterstitialAt = now
        ad.show(activity)
    }

    /** Shows a rewarded ad. [onReward] runs only if the player watched it to the end. */
    fun showRewarded(activity: Activity?, onClosed: () -> Unit = {}, onReward: () -> Unit) {
        if (premium) { onReward(); onClosed(); return }
        val ad = rewarded
        if (activity == null || ad == null) { onClosed(); return }
        var earned = false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null; rewardedReady = false; loadRewarded(activity)
                if (earned) onReward()
                onClosed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewarded = null; rewardedReady = false; loadRewarded(activity); onClosed()
            }
        }
        ad.show(activity) { earned = true }
    }
}

/** Adaptive banner that fills the screen width. Shows nothing until ads are ready. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    if (!Ads.ready || Ads.premium) return
    val width = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth().wrapContentHeight(),
        factory = { ctx ->
            AdView(ctx).apply {
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, width))
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
