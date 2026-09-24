package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import ch.hygro.padelscore.R
import ch.hygro.padelscore.billing.SupporterBillingAvailability
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.billing.SupporterPurchaseStatus

@Composable
fun SupporterScreen(
    state: SupporterBillingState,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(
                horizontal = 16.dp,
                vertical = 8.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (state.isSupporterActive) {
            ActiveSupporterContent()
        } else {
            InactiveSupporterContent(
                state = state,
                onPurchase = onPurchase,
                onRestorePurchases = onRestorePurchases
            )
        }

        SupporterActionButton(
            text = stringResource(R.string.back),
            enabled = true,
            backgroundColor = Color(0xFF3A3A3A),
            contentColor = Color.White,
            onClick = onBack
        )
    }
}

@Composable
private fun ActiveSupporterContent() {
    Text(
        text = stringResource(R.string.supporter_thanks_title),
        color = Color(0xFF66E59A),
        fontSize = 12.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.supporter_active_message),
        color = Color.White,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF163D29),
                shape = RoundedCornerShape(15.dp)
            )
            .padding(
                horizontal = 10.dp,
                vertical = 6.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.supporter_active_status),
            color = Color(0xFF66E59A),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun InactiveSupporterContent(
    state: SupporterBillingState,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit
) {
    Text(
        text = stringResource(R.string.support_padel_score_title),
        color = Color.White,
        fontSize = 12.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.support_message),
        color = Color.White,
        fontSize = 8.sp,
        lineHeight = 9.sp,
        textAlign = TextAlign.Center
    )

    Text(
        text = stringResource(R.string.support_info),
        color = Color.LightGray,
        fontSize = 7.sp,
        lineHeight = 8.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )

    when (state.purchaseStatus) {
        SupporterPurchaseStatus.PENDING -> {
            PendingPurchaseMessage()
        }

        SupporterPurchaseStatus.PURCHASE_IN_PROGRESS -> {
            PurchaseInProgressMessage()
        }

        SupporterPurchaseStatus.NOT_PURCHASED,
        SupporterPurchaseStatus.ACTIVE -> {
            PurchaseAvailabilityContent(
                state = state,
                onPurchase = onPurchase
            )
        }
    }

    state.userMessage?.let { message ->
        UserMessage(text = message)
    }

    SupporterActionButton(
        text = if (state.isRefreshing) {
            stringResource(R.string.checking_purchases)
        } else {
            stringResource(R.string.restore_purchases)
        },
        enabled = !state.isRefreshing &&
                state.purchaseStatus !=
                SupporterPurchaseStatus.PURCHASE_IN_PROGRESS,
        backgroundColor = Color(0xFF3A3A3A),
        contentColor = Color.White,
        onClick = onRestorePurchases
    )
}

@Composable
private fun PurchaseAvailabilityContent(
    state: SupporterBillingState,
    onPurchase: () -> Unit
) {
    when (state.availability) {
        SupporterBillingAvailability.AVAILABLE -> {
            val price = state.localizedPrice

            SupporterActionButton(
                text = if (price != null) {
                    stringResource(R.string.support_with_price, price)
                } else {
                    stringResource(R.string.loading_price)
                },
                enabled = state.canStartPurchase,
                backgroundColor = Color(0xFF195F3B),
                contentColor = Color.White,
                onClick = onPurchase
            )
        }

        SupporterBillingAvailability.CONNECTING,
        SupporterBillingAvailability.LOADING_PRODUCT -> {
            StatusMessage(
                text = stringResource(R.string.loading_price),
                color = Color.LightGray
            )
        }

        SupporterBillingAvailability.PRODUCT_UNAVAILABLE -> {
            StatusMessage(
                text = stringResource(R.string.product_unavailable),
                color = Color(0xFFFFD54F)
            )
        }

        SupporterBillingAvailability.BILLING_UNAVAILABLE -> {
            StatusMessage(
                text = stringResource(R.string.billing_unavailable),
                color = Color(0xFFFFD54F)
            )
        }
    }
}

@Composable
private fun PendingPurchaseMessage() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF3D3214),
                shape = RoundedCornerShape(15.dp)
            )
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = stringResource(R.string.purchase_pending_title),
            color = Color(0xFFFFD54F),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Text(
            text = stringResource(R.string.purchase_pending_message),
            color = Color.White,
            fontSize = 8.sp,
            lineHeight = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PurchaseInProgressMessage() {
    StatusMessage(
        text = stringResource(R.string.starting_purchase),
        color = Color.LightGray
    )
}

@Composable
private fun UserMessage(text: String) {
    Text(
        text = text,
        color = Color(0xFFFFD54F),
        fontSize = 8.sp,
        lineHeight = 10.sp,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun StatusMessage(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF292929),
                shape = RoundedCornerShape(15.dp)
            )
            .padding(
                horizontal = 9.dp,
                vertical = 6.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SupporterActionButton(
    text: String,
    enabled: Boolean,
    backgroundColor: Color,
    contentColor: Color,
    onClick: () -> Unit
) {
    val displayedBackgroundColor = if (enabled) {
        backgroundColor
    } else {
        backgroundColor.copy(alpha = 0.45f)
    }

    val displayedContentColor = if (enabled) {
        contentColor
    } else {
        contentColor.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth(0.90f)
            .heightIn(min = 29.dp)
            .background(
                color = displayedBackgroundColor,
                shape = RoundedCornerShape(17.dp)
            )
            .clickable(
                enabled = enabled,
                onClick = onClick
            )
            .padding(
                horizontal = 8.dp,
                vertical = 5.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = displayedContentColor,
            fontSize = 7.sp,
            lineHeight = 8.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
