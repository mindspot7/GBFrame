package gb.frame

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
        if (!unlocked.value) dialogVisible.value = true
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
        private var connecting = false
        private var restoreRequested = false
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

        fun connect() {
            if (client.isReady || connecting) return
            connecting = true
            client.startConnection(object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    connecting = false
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        refresh()
                    } else {
                        restoreRequested = false
                        if (!unlocked.value) status.value = Status.Error
                        message.value = "Could not connect to Google Play. Try again later."
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    if (!unlocked.value) status.value = Status.Error
                }
            })
        }

        fun refresh() {
            if (!client.isReady) {
                connect()
                return
            }
            if (!unlocked.value) status.value = Status.Checking
            product = null
            queryProduct()
            val showRestoreResult = restoreRequested
            restoreRequested = false
            queryOwnedPurchases(showRestoreResult)
        }

        fun restore() {
            message.value = "Checking purchases for this Google Play account…"
            if (!client.isReady) {
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
                price.value = product?.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
                if (product == null && !unlocked.value) {
                    status.value = Status.Unavailable
                    message.value = "This app's one-time product is not available in Google Play yet."
                } else if (!unlocked.value) {
                    status.value = Status.Ready
                    message.value = null
                }
            }
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
                    grant(ownedPurchase)
                    if (showRestoreResult) message.value = "Themes are unlocked. Thanks for supporting development."
                } else {
                    setOwned(false)
                    if (product != null) status.value = Status.Ready else status.value = Status.Unavailable
                    if (showRestoreResult) message.value = "No visual customization purchase was found for this account."
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
                Purchase.PurchaseState.PURCHASED -> grant(purchase)
                Purchase.PurchaseState.PENDING -> message.value = "The purchase is pending. It unlocks after Google Play confirms payment."
                else -> Unit
            }
        }

        private fun grant(purchase: Purchase) {
            val showPurchaseConfirmation = dialogVisible.value
            setOwned(true)
            message.value = null
            if (showPurchaseConfirmation) {
                message.value = "Thanks for supporting development. All themes are unlocked."
            }
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                client.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK)
                        message.value = "Unlocked, but Google Play could not confirm the purchase yet. Reopen the app to retry."
                }
            }
        }

        private fun setOwned(value: Boolean) {
            unlocked.value = value
            status.value = if (value) Status.Owned else if (product != null) Status.Ready else Status.Unavailable
            preferences.edit().putBoolean(OWNED, value).apply()
        }

        fun buy(activity: Activity) {
            val details = product
            val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
            val offerToken = offer?.offerToken
            if (!client.isReady || details == null || offerToken.isNullOrBlank()) {
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
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(25.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(if (unlocked) "Themes unlocked" else "Gold themes",
                                style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                            Text(if (unlocked) appName else "Support the developer",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Text(
                        if (unlocked) message ?: "Thanks for supporting development. All themes are unlocked in $appName."
                        else "Make a one-time purchase to support development and unlock all themes in $appName.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (!unlocked) {
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = if (price != null) "ONE-TIME PURCHASE  ·  $price" else "ONE-TIME PURCHASE",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (status == VisualCustomization.Status.Checking) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(
                            enabled = status == VisualCustomization.Status.Ready,
                            onClick = { VisualCustomization.buy(activity) },
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                        ) {
                            Text("👑", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.width(8.dp))
                            Text(if (price == null) "Support & unlock" else "Support · $price")
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextButton(onClick = VisualCustomization::restore) { Text("Restore purchase") }
                            TextButton(onClick = VisualCustomization::dismissDialog) { Text("Not now") }
                        }
                    } else {
                        Button(onClick = VisualCustomization::dismissDialog,
                            modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
                            Text("Done")
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
