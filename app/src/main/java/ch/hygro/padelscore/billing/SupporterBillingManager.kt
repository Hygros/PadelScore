package ch.hygro.padelscore.billing

import android.app.Activity
import android.content.Context
import ch.hygro.padelscore.R
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

class SupporterBillingManager(
    context: Context,
    initialState: SupporterBillingState = SupporterBillingState(),
    private val onStateChanged: (SupporterBillingState) -> Unit
) : PurchasesUpdatedListener {

    private val applicationContext = context.applicationContext

    private var state = initialState
    private var productDetails: ProductDetails? = null
    private var connectionStarted = false
    private var purchaseFlowInProgress = false
    private var closed = false

    private val billingClient: BillingClient =
        BillingClient.newBuilder(applicationContext)
            .setListener(this)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()

    fun currentState(): SupporterBillingState = state

    fun start() {
        if (closed) return

        if (billingClient.isReady) {
            refresh()
            return
        }

        if (connectionStarted) return

        connectionStarted = true
        updateState(
            state.copy(
                availability = SupporterBillingAvailability.CONNECTING,
                isRefreshing = true,
                userMessage = null
            )
        )

        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(
                    billingResult: BillingResult
                ) {
                    connectionStarted = false

                    if (closed) return

                    if (
                        billingResult.responseCode ==
                        BillingClient.BillingResponseCode.OK
                    ) {
                        loadProductAndPurchases()
                    } else {
                        handleBillingUnavailable(billingResult)
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connectionStarted = false

                    if (closed) return

                    updateState(
                        state.copy(
                            availability =
                                SupporterBillingAvailability.CONNECTING,
                            isRefreshing = false,
                            userMessage =
                                applicationContext.getString(R.string.billing_service_disconnected)
                        )
                    )
                }
            }
        )
    }

    fun refresh() {
        if (closed) return

        if (!billingClient.isReady) {
            connectionStarted = false
            start()
            return
        }

        loadProductAndPurchases()
    }

    fun launchPurchase(activity: Activity) {
        if (closed) return
        if (purchaseFlowInProgress) return
        if (!state.canStartPurchase) return

        val currentProductDetails = productDetails

        if (currentProductDetails == null) {
            updateState(
                state.copy(
                    availability =
                        SupporterBillingAvailability.PRODUCT_UNAVAILABLE,
                    userMessage =
                        applicationContext.getString(R.string.supporter_product_unavailable)
                )
            )
            return
        }

        val offerDetails =
            currentProductDetails.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()

        if (offerDetails == null) {
            updateState(
                state.copy(
                    availability =
                        SupporterBillingAvailability.PRODUCT_UNAVAILABLE,
                    userMessage =
                        applicationContext.getString(R.string.no_offer_available)
                )
            )
            return
        }

        purchaseFlowInProgress = true
        updateState(
            state.copy(
                purchaseStatus =
                    SupporterPurchaseStatus.PURCHASE_IN_PROGRESS,
                userMessage = null
            )
        )

        val productDetailsParamsBuilder =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(currentProductDetails)

        offerDetails.offerToken?.let { offerToken ->
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }

        val productDetailsParams =
            productDetailsParamsBuilder.build()

        val billingFlowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(
                    listOf(productDetailsParams)
                )
                .build()

        val billingResult =
            billingClient.launchBillingFlow(
                activity,
                billingFlowParams
            )

        if (
            billingResult.responseCode !=
            BillingClient.BillingResponseCode.OK
        ) {
            purchaseFlowInProgress = false
            updateState(
                state.copy(
                    purchaseStatus =
                        SupporterPurchaseStatus.NOT_PURCHASED,
                    userMessage =
                        userMessageForBillingResult(billingResult)
                )
            )
        }
    }

    fun close() {
        if (closed) return

        closed = true
        connectionStarted = false
        purchaseFlowInProgress = false
        productDetails = null

        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    override fun onPurchasesUpdated(
        billingResult: BillingResult,
        purchases: List<Purchase>?
    ) {
        if (closed) return

        purchaseFlowInProgress = false

        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                processPurchases(purchases.orEmpty())
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                updateState(
                    state.copy(
                        purchaseStatus =
                            SupporterPurchaseStatus.NOT_PURCHASED,
                        userMessage = null
                    )
                )
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                queryExistingPurchases()
            }

            else -> {
                updateState(
                    state.copy(
                        purchaseStatus =
                            SupporterPurchaseStatus.NOT_PURCHASED,
                        userMessage =
                            userMessageForBillingResult(billingResult)
                    )
                )
            }
        }
    }

    private fun loadProductAndPurchases() {
        if (closed) return

        updateState(
            state.copy(
                availability =
                    SupporterBillingAvailability.LOADING_PRODUCT,
                isRefreshing = true,
                userMessage = null
            )
        )

        queryProductDetails()
        queryExistingPurchases()
    }

    private fun queryProductDetails() {
        val product =
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(SUPPORTER_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(listOf(product))
                .build()

        billingClient.queryProductDetailsAsync(params) {
                billingResult,
                productDetailsResult ->

            if (closed) return@queryProductDetailsAsync

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {
                productDetails = null
                updateState(
                    state.copy(
                        availability =
                            availabilityAfterProductFailure(),
                        localizedPrice = null,
                        isRefreshing = false,
                        userMessage =
                            userMessageForBillingResult(billingResult)
                    )
                )
                return@queryProductDetailsAsync
            }

            val matchingProduct =
                productDetailsResult.productDetailsList
                    .firstOrNull {
                        it.productId == SUPPORTER_PRODUCT_ID
                    }

            val matchingOffer =
                matchingProduct
                    ?.oneTimePurchaseOfferDetailsList
                    ?.firstOrNull()

            if (matchingProduct == null || matchingOffer == null) {
                productDetails = null
                updateState(
                    state.copy(
                        availability =
                            SupporterBillingAvailability.PRODUCT_UNAVAILABLE,
                        localizedPrice = null,
                        isRefreshing = false,
                        userMessage =
                            applicationContext.getString(R.string.supporter_product_unavailable)
                    )
                )
                return@queryProductDetailsAsync
            }

            productDetails = matchingProduct
            updateState(
                state.copy(
                    availability =
                        SupporterBillingAvailability.AVAILABLE,
                    localizedPrice = matchingOffer.formattedPrice,
                    isRefreshing = false,
                    userMessage = null
                )
            )
        }
    }

    private fun queryExistingPurchases() {
        val params =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()

        billingClient.queryPurchasesAsync(params) {
                billingResult,
                purchases ->

            if (closed) return@queryPurchasesAsync

            if (
                billingResult.responseCode !=
                BillingClient.BillingResponseCode.OK
            ) {
                updateState(
                    state.copy(
                        isRefreshing = false,
                        userMessage =
                            userMessageForBillingResult(billingResult)
                    )
                )
                return@queryPurchasesAsync
            }

            processPurchases(purchases)
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        if (closed) return

        val supporterPurchases =
            purchases.filter {
                SUPPORTER_PRODUCT_ID in it.products
            }

        val purchased =
            supporterPurchases.firstOrNull {
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }

        if (purchased != null) {
            processCompletedPurchase(purchased)
            return
        }

        val pending =
            supporterPurchases.any {
                it.purchaseState == Purchase.PurchaseState.PENDING
            }

        updateState(
            state.copy(
                purchaseStatus =
                    if (pending) {
                        SupporterPurchaseStatus.PENDING
                    } else {
                        SupporterPurchaseStatus.NOT_PURCHASED
                    },
                isRefreshing = false,
                userMessage =
                    if (pending) {
                        applicationContext.getString(R.string.purchase_pending_user_message)
                    } else {
                        null
                    }
            )
        )
    }

    private fun processCompletedPurchase(purchase: Purchase) {
        if (purchase.isAcknowledged) {
            activateSupporter()
            return
        }

        val acknowledgeParams =
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build()

        billingClient.acknowledgePurchase(acknowledgeParams) {
                billingResult ->

            if (closed) return@acknowledgePurchase

            if (
                billingResult.responseCode ==
                BillingClient.BillingResponseCode.OK
            ) {
                activateSupporter()
            } else {
                updateState(
                    state.copy(
                        purchaseStatus =
                            SupporterPurchaseStatus.NOT_PURCHASED,
                        isRefreshing = false,
                        userMessage =
                            applicationContext.getString(R.string.purchase_acknowledge_error)
                    )
                )
            }
        }
    }

    private fun activateSupporter() {
        purchaseFlowInProgress = false
        updateState(
            state.copy(
                purchaseStatus = SupporterPurchaseStatus.ACTIVE,
                isRefreshing = false,
                userMessage = null
            )
        )
    }

    private fun handleBillingUnavailable(
        billingResult: BillingResult
    ) {
        productDetails = null
        purchaseFlowInProgress = false

        updateState(
            state.copy(
                availability =
                    SupporterBillingAvailability.BILLING_UNAVAILABLE,
                localizedPrice = null,
                isRefreshing = false,
                userMessage =
                    userMessageForBillingResult(billingResult)
            )
        )
    }

    private fun availabilityAfterProductFailure():
        SupporterBillingAvailability {

        return if (billingClient.isReady) {
            SupporterBillingAvailability.PRODUCT_UNAVAILABLE
        } else {
            SupporterBillingAvailability.BILLING_UNAVAILABLE
        }
    }

    private fun userMessageForBillingResult(
        billingResult: BillingResult
    ): String {
        return when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.SERVICE_DISCONNECTED,
            BillingClient.BillingResponseCode.SERVICE_UNAVAILABLE,
            BillingClient.BillingResponseCode.NETWORK_ERROR -> {
                applicationContext.getString(R.string.billing_service_disconnected)
            }

            BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> {
                applicationContext.getString(R.string.billing_unavailable_on_device)
            }

            BillingClient.BillingResponseCode.DEVELOPER_ERROR -> {
                applicationContext.getString(R.string.billing_setup_developer_error)
            }

            BillingClient.BillingResponseCode.ITEM_UNAVAILABLE -> {
                applicationContext.getString(R.string.supporter_product_unavailable)
            }

            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> {
                applicationContext.getString(R.string.purchase_already_owned)
            }

            BillingClient.BillingResponseCode.ERROR -> {
                applicationContext.getString(R.string.purchase_error)
            }

            else -> {
                applicationContext.getString(R.string.generic_billing_error)
            }
        }
    }

    private fun updateState(newState: SupporterBillingState) {
        if (closed) return

        state = newState
        onStateChanged(newState)
    }
}
