package gb.frame

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import java.util.concurrent.Executors

/**
 * The shared, permanent purchase that unlocks the optional library themes.
 * The two apps each create this product ID in their own Play Console listing.
 */
object VisualCustomization {
    private const val PREFS = "gbframe_visual_customization"
    private const val OWNED = "visual_pack_owned"

    enum class Status { Checking, Ready, Owned, Unavailable, Error }

    val unlocked = mutableStateOf(false)
    val dialogVisible = mutableStateOf(false)
    val status = mutableStateOf(Status.Checking)
    val price = mutableStateOf<String?>(null)
    val message = mutableStateOf<String?>(null)

    private var manager: Manager? = null

    /** Requests the shared purchase dialog. It is rendered by [VisualCustomizationPurchaseHost]. */
    fun requestUnlock() {
        if (!unlocked.value) {
            dialogVisible.value = true
            refreshProduct()
        }
    }

    fun dismissDialog() {
        dialogVisible.value = false
    }

    /** Returns whether the user can apply a paid theme. Safe to call from UI entry points. */
    fun allowThemeUnlock(): Boolean {
        if (unlocked.value) return true
        requestUnlock()
        return false
    }

    internal fun configure(context: Context, productId: String) {
        val appContext = context.applicationContext
        if (manager?.matches(appContext, productId) == true) return
        manager?.close()
        manager = Manager(appContext, productId).also { it.connect() }
    }

    internal fun refresh() = manager?.refresh()

    internal fun refreshProduct() = manager?.refreshProduct()

    internal fun buy(activity: Activity?) {
        val current = manager
        if (activity == null || current == null) {
            message.value = "Google Play purchase is unavailable in this build."
        } else {
            current.buy(activity)
        }
    }

    internal fun restore() = manager?.restore() ?: run {
        status.value = Status.Error
        message.value = "Google Play purchase is unavailable in this build."
    }

    private class Manager(private val context: Context, private val productId: String) : PurchasesUpdatedListener {
        private val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        private var product: ProductDetails? = null
        private var selectedOffer: ProductDetails.OneTimePurchaseOfferDetails? = null
        private var connecting = false
        private var setupDone = false
        private var restoreRequested = false
        private var closed = false
        private val pendingVerificationTokens = mutableSetOf<String>()
        private val mainHandler = Handler(Looper.getMainLooper())
        private val licenseExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "gbframe-gold-license").apply { isDaemon = true }
        }
        private val client = BillingClient.newBuilder(context)
            .setListener(this)
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()

        init {
            unlocked.value = preferences.getBoolean(OWNED, false)
            status.value = if (unlocked.value) Status.Owned else Status.Checking
            price.value = null
            message.value = null
        }

        fun matches(appContext: Context, requestedProductId: String) =
            context === appContext && productId == requestedProductId

        // startConnection runs once: enableAutoServiceReconnection restores a lost connection by itself on the next call,
        // and calling startConnection again while it does so fails ("Reconnection failed with result: 5").
        fun connect() {
            if (setupDone || connecting) return
            connecting = true
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        setupDone = true
                        refresh()
                    } else {
                        restoreRequested = false
                        if (!unlocked.value) status.value = Status.Error
                        message.value = "Could not connect to Google Play. Try again later."
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false // the library reconnects on the next request
                }
            })
        }

        fun refresh() {
            if (!setupDone) {
                connect()
                return
            }
            if (!unlocked.value) status.value = Status.Checking
            refreshProduct()
            val showRestoreResult = restoreRequested
            restoreRequested = false
            queryOwnedPurchases(showRestoreResult)
        }

        fun refreshProduct() {
            if (!setupDone) {
                connect()
                return
            }
            if (!unlocked.value) status.value = Status.Checking
            product = null
            selectedOffer = null
            price.value = null
            message.value = null
            queryProduct()
        }

        fun restore() {
            message.value = "Checking purchases for this Google Play account…"
            if (!setupDone) {
                restoreRequested = true
                connect()
                return
            }
            queryOwnedPurchases(showRestoreResult = true)
        }

        private fun queryProduct() {
            val item = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(item)).build()
            client.queryProductDetailsAsync(params) { result, detailsResult ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (!unlocked.value) status.value = Status.Error
                    message.value = "Could not load the purchase from Google Play."
                    return@queryProductDetailsAsync
                }
                product = detailsResult.productDetailsList.firstOrNull { it.productId == productId }
                selectedOffer = product?.let(::selectOffer)
                price.value = selectedOffer?.formattedPrice
                if (product == null && !unlocked.value) {
                    status.value = Status.Unavailable
                    message.value = "This app's one-time product is not available in Google Play yet."
                } else if (selectedOffer == null && !unlocked.value) {
                    status.value = Status.Unavailable
                    message.value = "No eligible purchase offer is available for this Google Play account."
                } else if (!unlocked.value) {
                    status.value = Status.Ready
                    message.value = null
                }
            }
        }

        /** Prefer the lowest-priced eligible discounted buy offer, then the lowest-priced buy offer. */
        private fun selectOffer(details: ProductDetails): ProductDetails.OneTimePurchaseOfferDetails? {
            val eligibleBuyOffers = details.oneTimePurchaseOfferDetailsList.orEmpty()
                .filter { it.rentalDetails == null && it.offerToken.isNotBlank() }
            val discountedOffers = eligibleBuyOffers.filter {
                it.discountDisplayInfo != null || (it.fullPriceMicros ?: it.priceAmountMicros) > it.priceAmountMicros
            }
            return (discountedOffers.ifEmpty { eligibleBuyOffers }).minByOrNull { it.priceAmountMicros }
        }

        private fun queryOwnedPurchases(showRestoreResult: Boolean) {
            val params = QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
            client.queryPurchasesAsync(params) { result, purchases ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    if (!unlocked.value) status.value = Status.Error
                    if (showRestoreResult) message.value = "Could not check Google Play purchases. Try again later."
                    return@queryPurchasesAsync
                }
                val ownedPurchase = purchases.firstOrNull {
                    productId in it.products && it.purchaseState == Purchase.PurchaseState.PURCHASED
                }
                if (ownedPurchase != null) {
                    val operation = if (ownedPurchase.isAcknowledged) GoldLicenseApi.Operation.CheckLicense
                    else GoldLicenseApi.Operation.VerifyPurchase
                    verifyLicense(ownedPurchase, operation, showRestoreResult)
                } else {
                    setOwned(false)
                    if (selectedOffer != null) status.value = Status.Ready else status.value = Status.Unavailable
                    if (showRestoreResult) message.value = "No Gold purchase was found for this Google Play account."
                }
            }
        }

        override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
            when (result.responseCode) {
                BillingClient.BillingResponseCode.OK -> purchases.orEmpty().forEach(::processPurchase)
                BillingClient.BillingResponseCode.USER_CANCELED -> message.value = null
                BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> refresh()
                else -> message.value = "Google Play could not complete the purchase. Try again later."
            }
        }

        private fun processPurchase(purchase: Purchase) {
            if (productId !in purchase.products) return
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> verifyLicense(
                    purchase,
                    GoldLicenseApi.Operation.VerifyPurchase,
                    showRestoreResult = false,
                )
                Purchase.PurchaseState.PENDING -> message.value = "The purchase is pending. It unlocks after Google Play confirms payment."
                else -> Unit
            }
        }

        private fun verifyLicense(purchase: Purchase, operation: GoldLicenseApi.Operation, showRestoreResult: Boolean) {
            val token = purchase.purchaseToken
            if (closed || !pendingVerificationTokens.add(token)) return
            if (!unlocked.value) status.value = Status.Checking
            if (showRestoreResult || dialogVisible.value) message.value = "Checking your Gold license…"
            try {
                licenseExecutor.execute {
                    var result = GoldLicenseApi.validate(context, productId, token, operation)
                    // A Play purchase may predate this backend. If its server record is missing, verify the
                    // still-owned Play token and let the backend register the license before denying access.
                    if (operation == GoldLicenseApi.Operation.CheckLicense &&
                        result is GoldLicenseApi.Result.Rejected && result.reason != "purchase_canceled") {
                        result = GoldLicenseApi.validate(context, productId, token, GoldLicenseApi.Operation.VerifyPurchase)
                    }
                    mainHandler.post {
                        pendingVerificationTokens.remove(token)
                        if (closed) return@post
                        when (result) {
                            GoldLicenseApi.Result.Licensed -> {
                                setOwned(true)
                                acknowledgeIfNeeded(purchase)
                                message.value = when {
                                    showRestoreResult -> "Gold themes restored. Thanks for supporting development."
                                    dialogVisible.value -> "Thanks for supporting development. All themes are unlocked."
                                    else -> null
                                }
                            }
                            is GoldLicenseApi.Result.Rejected -> {
                                setOwned(false)
                                message.value = when (result.reason) {
                                    "purchase_canceled" -> "This purchase was canceled or refunded. Gold themes remain locked."
                                    "purchase_not_found" -> "Google Play could not confirm this purchase. Gold themes remain locked."
                                    else -> if (showRestoreResult) "No verified Gold license was found for this Google Play account."
                                    else "Google Play could not verify the Gold purchase. Try again or restore the purchase."
                                }
                            }
                            GoldLicenseApi.Result.Unavailable -> {
                                if (!unlocked.value) status.value = if (selectedOffer != null) Status.Ready else Status.Error
                                message.value = if (unlocked.value) {
                                    "The license server is unavailable. Previously unlocked themes remain available."
                                } else {
                                    "Could not reach the license server. Connect to the internet and try again."
                                }
                            }
                        }
                    }
                }
            } catch (_: java.util.concurrent.RejectedExecutionException) {
                pendingVerificationTokens.remove(token)
            }
        }

        // The license backend acknowledges purchases too; this client-side acknowledgement keeps a verified purchase
        // from being refunded automatically by Google Play after 3 days if the backend could not do it.
        private fun acknowledgeIfNeeded(purchase: Purchase) {
            if (purchase.isAcknowledged || closed) return
            val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
            client.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    android.util.Log.w("GBFrame-Gold", "acknowledgePurchase: ${result.responseCode} ${result.debugMessage}")
                }
            }
        }

        private fun setOwned(value: Boolean) {
            unlocked.value = value
            status.value = if (value) Status.Owned else if (selectedOffer != null) Status.Ready else Status.Unavailable
            preferences.edit().putBoolean(OWNED, value).apply()
        }

        fun buy(activity: Activity) {
            val details = product
            val offer = selectedOffer
            val offerToken = offer?.offerToken
            if (!setupDone || details == null || offerToken.isNullOrBlank()) {
                message.value = "The purchase is not available right now. Try again later."
                refresh()
                return
            }
            val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
            val params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
            val result = client.launchBillingFlow(activity, params)
            if (result.responseCode != BillingClient.BillingResponseCode.OK)
                message.value = "Google Play could not start the purchase. Try again later."
        }

        fun close() {
            closed = true
            licenseExecutor.shutdownNow()
            client.endConnection()
        }
    }
}

/** Shared purchase dialog host. Add once to each app's game-library root. */
@Composable
fun VisualCustomizationPurchaseHost(appName: String, productId: String) {
    val context = LocalContext.current
    DisposableEffect(context, productId) {
        VisualCustomization.configure(context, productId)
        onDispose { }
    }
    LifecycleResumeEffect(productId) {
        VisualCustomization.refresh()
        onPauseOrDispose { }
    }

    val unlocked by VisualCustomization.unlocked
    val visible by VisualCustomization.dialogVisible
    val status by VisualCustomization.status
    val price by VisualCustomization.price
    val message by VisualCustomization.message
    if (visible) {
        val activity = remember(context) { context.findActivity() }
        Dialog(onDismissRequest = VisualCustomization::dismissDialog) {
            Surface(
                modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 400.dp),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Palette, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        KitText("$appName - PRO", style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
                        KitText(if (unlocked) "Themes unlocked" else "Support the developer",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center)
                    }

                    KitText(
                        if (unlocked) message ?: "Thanks for supporting development. All themes are unlocked in $appName."
                        else "Make a one-time purchase to support development and unlock all themes in $appName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    if (!unlocked) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            KitText(
                                text = "ONE-TIME PURCHASE",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        price?.let {
                            KitText(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary,
                                textAlign = TextAlign.Center,
                            )
                        }
                        if (status == VisualCustomization.Status.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        message?.let {
                            KitText(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        }
                        Button(
                            enabled = status == VisualCustomization.Status.Ready,
                            onClick = { VisualCustomization.buy(activity) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            KitText("👑", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            KitText("Support and unlock", textAlign = TextAlign.Center)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = VisualCustomization::restore) { KitText("Restore purchase") }
                            TextButton(onClick = VisualCustomization::dismissDialog) { KitText("Not now") }
                        }
                    } else {
                        Button(onClick = VisualCustomization::dismissDialog,
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                            KitText("Done")
                        }
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (true) {
        if (current is Activity) return current
        if (current !is ContextWrapper) return null
        val base = current.baseContext
        if (base === current) return null
        current = base
    }
}
