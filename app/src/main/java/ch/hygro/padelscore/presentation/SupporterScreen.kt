package ch.hygro.padelscore.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import ch.hygro.padelscore.R
import ch.hygro.padelscore.billing.SupporterBillingAvailability
import ch.hygro.padelscore.billing.SupporterBillingState
import ch.hygro.padelscore.billing.SupporterPurchaseStatus
import ch.hygro.padelscore.presentation.theme.PadelScoreTheme

@Composable
fun SupporterScreen(
    state: SupporterBillingState,
    onPurchase: () -> Unit,
    onRestorePurchases: () -> Unit,
    onBack: () -> Unit
) {
    val columnState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    val isRound = LocalConfiguration.current.isScreenRound

    ScreenScaffold(scrollState = columnState) { _ ->
        TransformingLazyColumn(
            state = columnState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = 10.dp,
                bottom = 14.dp,
                start = 8.dp,
                end = 8.dp
            ),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val itemScope = this
                ListHeader(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec),
                    transformation = with(itemScope) { SurfaceTransformation(transformationSpec) }
                ) {
                    Text(
                        text = if (state.isSupporterActive) {
                            stringResource(R.string.supporter_thanks_title)
                        } else {
                            stringResource(R.string.support_padel_score_title)
                        },
                        color = if (state.isSupporterActive) Color(0xFF66E59A) else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }

            if (state.isSupporterActive) {
                item {
                    val itemScope = this
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.88f else 1f)
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.supporter_active_message),
                            color = Color.White,
                            fontSize = 9.sp,
                            lineHeight = 10.sp,
                            textAlign = TextAlign.Center
                        )

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color(0xFF163D29),
                                    shape = RoundedCornerShape(15.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 6.dp),
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
                }
            } else {
                item {
                    val itemScope = this
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.88f else 1f)
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 3.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
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
                    }
                }

                item {
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.88f else 1f)
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 3.dp)
                    ) {
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
                    }
                }

                state.userMessage?.let { message ->
                    item {
                        val itemScope = this
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (isRound) 0.88f else 1f)
                                .transformedHeight(itemScope, transformationSpec)
                                .padding(vertical = 2.dp)
                        ) {
                            UserMessage(text = message)
                        }
                    }
                }

                item {
                    val itemScope = this
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(itemScope, transformationSpec)
                            .padding(vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Button(
                            modifier = Modifier
                                .fillMaxWidth(if (isRound) 0.82f else 0.88f)
                                .height(48.dp),
                            enabled = !state.isRefreshing &&
                                    state.purchaseStatus != SupporterPurchaseStatus.PURCHASE_IN_PROGRESS,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A3A3A),
                                contentColor = Color.White
                            ),
                            onClick = onRestorePurchases
                        ) {
                            Text(
                                text = if (state.isRefreshing) {
                                    stringResource(R.string.checking_purchases)
                                } else {
                                    stringResource(R.string.restore_purchases)
                                },
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            item {
                val itemScope = this
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .transformedHeight(itemScope, transformationSpec)
                        .padding(top = 6.dp, bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Button(
                        modifier = Modifier
                            .fillMaxWidth(if (isRound) 0.82f else 0.88f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF3A3A3A),
                            contentColor = Color.White
                        ),
                        onClick = onBack
                    ) {
                        Text(
                            text = stringResource(R.string.back),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PurchaseAvailabilityContent(
    state: SupporterBillingState,
    onPurchase: () -> Unit
) {
    val isRound = LocalConfiguration.current.isScreenRound
    
    when (state.availability) {
        SupporterBillingAvailability.AVAILABLE -> {
            val price = state.localizedPrice

            Button(
                modifier = Modifier
                    .fillMaxWidth(if (isRound) 0.82f else 0.88f)
                    .height(48.dp),
                enabled = state.canStartPurchase,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF195F3B),
                    contentColor = Color.White
                ),
                onClick = onPurchase
            ) {
                Text(
                    text = if (price != null) {
                        stringResource(R.string.support_with_price, price)
                    } else {
                        stringResource(R.string.loading_price)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
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

@WearPreviewDevices
@Composable
private fun SupporterScreenPreview() {
    PadelScoreTheme {
        SupporterScreen(
            state = SupporterBillingState(),
            onPurchase = {},
            onRestorePurchases = {},
            onBack = {}
        )
    }
}
