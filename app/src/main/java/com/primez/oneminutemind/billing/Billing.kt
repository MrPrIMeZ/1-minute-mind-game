package com.primez.oneminutemind.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

/**
 * Google Play one-time purchase "premium_lifetime".
 * Create this product in Play Console → Monetize → Products → In-app products,
 * with the same ID, and set the price there (e.g. ₹99).
 */
object Billing {
    const val PRODUCT_ID = "premium_lifetime"
    private const val TAG = "Billing"

    private var client: BillingClient? = null
    private var details: ProductDetails? = null

    /** Called with true when Premium is bought or restored. */
    var onPremium: (Boolean) -> Unit = {}

    /** Price text from Google Play, e.g. "₹99.00"; null until Play answers. */
    var price by mutableStateOf<String?>(null)
        private set

    /** False when Play doesn't offer the product yet (e.g. before the app is on the Play Store). */
    var available by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    fun start(context: Context) {
        if (client != null) return
        val c = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> handle(result, purchases) }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build()
        client = c
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    Log.w(TAG, "Setup: ${result.debugMessage}"); return
                }
                loadProduct()
                restore()
            }

            override fun onBillingServiceDisconnected() {
                client = null // start() will reconnect next time
            }
        })
    }

    private fun loadProduct() {
        val params = QueryProductDetailsParams.newBuilder().setProductList(
            listOf(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(PRODUCT_ID)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            ),
        ).build()
        client?.queryProductDetailsAsync(params) { result, list ->
            val d = list.firstOrNull()
            if (result.responseCode == BillingClient.BillingResponseCode.OK && d != null) {
                details = d
                price = d.oneTimePurchaseOfferDetails?.formattedPrice
                available = true
            }
        }
    }

    /** Finds an earlier purchase (new phone or reinstall). */
    fun restore() {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client?.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) handle(result, purchases)
        }
    }

    /** Opens the Google Play purchase sheet. Returns false if Premium can't be bought yet. */
    fun buy(activity: Activity): Boolean {
        val c = client ?: run { start(activity); return false }
        val d = details ?: return false
        busy = true
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(
            listOf(BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).build()),
        ).build()
        c.launchBillingFlow(activity, params)
        return true
    }

    private fun handle(result: BillingResult, purchases: List<Purchase>?) {
        busy = false
        if (result.responseCode != BillingClient.BillingResponseCode.OK || purchases == null) return
        purchases.filter { PRODUCT_ID in it.products }.forEach { p ->
            if (p.purchaseState == Purchase.PurchaseState.PURCHASED) {
                if (!p.isAcknowledged) {
                    val ack = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.purchaseToken).build()
                    client?.acknowledgePurchase(ack) { r -> Log.i(TAG, "Acknowledged: ${r.responseCode}") }
                }
                onPremium(true)
            }
        }
    }
}
