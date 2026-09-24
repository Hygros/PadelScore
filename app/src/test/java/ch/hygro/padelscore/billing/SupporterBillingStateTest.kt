package ch.hygro.padelscore.billing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupporterBillingStateTest {

    @Test
    fun defaultStateDoesNotAllowPurchase() {
        val state = SupporterBillingState()

        assertFalse(state.canStartPurchase)
        assertFalse(state.isSupporterActive)
        assertFalse(state.isPurchasePending)
    }

    @Test
    fun availableProductWithPriceAllowsPurchase() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.NOT_PURCHASED,
            localizedPrice = "CHF 4.90"
        )

        assertTrue(state.canStartPurchase)
    }

    @Test
    fun availableProductWithoutPriceDoesNotAllowPurchase() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.NOT_PURCHASED,
            localizedPrice = null
        )

        assertFalse(state.canStartPurchase)
    }

    @Test
    fun activeSupporterCannotStartAnotherPurchase() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.ACTIVE,
            localizedPrice = "CHF 4.90"
        )

        assertTrue(state.isSupporterActive)
        assertFalse(state.canStartPurchase)
    }

    @Test
    fun pendingPurchaseDoesNotActivateSupporterStatus() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.PENDING,
            localizedPrice = "CHF 4.90"
        )

        assertTrue(state.isPurchasePending)
        assertFalse(state.isSupporterActive)
        assertFalse(state.canStartPurchase)
    }

    @Test
    fun purchaseInProgressPreventsRepeatedPurchaseStart() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.PURCHASE_IN_PROGRESS,
            localizedPrice = "CHF 4.90"
        )

        assertFalse(state.canStartPurchase)
    }

    @Test
    fun refreshingStatePreventsPurchaseStart() {
        val state = SupporterBillingState(
            availability = SupporterBillingAvailability.AVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.NOT_PURCHASED,
            localizedPrice = "CHF 4.90",
            isRefreshing = true
        )

        assertFalse(state.canStartPurchase)
    }

    @Test
    fun productUnavailableDoesNotAllowPurchase() {
        val state = SupporterBillingState(
            availability =
                SupporterBillingAvailability.PRODUCT_UNAVAILABLE,
            purchaseStatus = SupporterPurchaseStatus.NOT_PURCHASED
        )

        assertFalse(state.canStartPurchase)
    }
}
