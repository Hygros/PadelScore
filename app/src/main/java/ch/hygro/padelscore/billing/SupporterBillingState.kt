package ch.hygro.padelscore.billing

enum class SupporterBillingAvailability {
    CONNECTING,
    LOADING_PRODUCT,
    AVAILABLE,
    PRODUCT_UNAVAILABLE,
    BILLING_UNAVAILABLE
}

enum class SupporterPurchaseStatus {
    NOT_PURCHASED,
    PURCHASE_IN_PROGRESS,
    PENDING,
    ACTIVE
}

data class SupporterBillingState(
    val availability: SupporterBillingAvailability =
        SupporterBillingAvailability.CONNECTING,
    val purchaseStatus: SupporterPurchaseStatus =
        SupporterPurchaseStatus.NOT_PURCHASED,
    val localizedPrice: String? = null,
    val isRefreshing: Boolean = false,
    val userMessage: String? = null
) {
    val isSupporterActive: Boolean
        get() = purchaseStatus == SupporterPurchaseStatus.ACTIVE

    val isPurchasePending: Boolean
        get() = purchaseStatus == SupporterPurchaseStatus.PENDING

    val canStartPurchase: Boolean
        get() =
            availability == SupporterBillingAvailability.AVAILABLE &&
                purchaseStatus == SupporterPurchaseStatus.NOT_PURCHASED &&
                localizedPrice != null &&
                !isRefreshing
}

const val SUPPORTER_PRODUCT_ID = "supporter_lifetime"
