package ch.hygro.padelscore.health

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
import ch.hygro.padelscore.R
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds

internal object CompanionSetup {
    suspend fun status(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val connected = Tasks.await(
                Wearable.getNodeClient(context).connectedNodes, 10, TimeUnit.SECONDS
            )
            var discoveryFailed = false
            val advertised: Set<Node> = try {
                Tasks.await(
                    Wearable.getCapabilityClient(context).getCapability(
                        WorkoutSyncProtocol.RECEIVER, CapabilityClient.FILTER_REACHABLE
                    ), 10, TimeUnit.SECONDS
                ).nodes
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                discoveryFailed = true
                emptySet()
            }
            val targets = (advertised.toList() + connected)
                .distinctBy { it.id }
                .sortedByDescending { it.isNearby }
            if (targets.isEmpty()) {
                return@withContext context.getString(R.string.companion_status_no_reachable_device)
            }
            val failures = mutableListOf<String>()
            for (node in targets) {
                currentCoroutineContext().ensureActive()
                try {
                    val bytes = Tasks.await(
                        Wearable.getMessageClient(context).sendRequest(
                            node.id, WorkoutSyncProtocol.STATUS, byteArrayOf()
                        ), 10, TimeUnit.SECONDS
                    )
                    require(bytes.isNotEmpty() && bytes.size <= 4096)
                    val reply = String(bytes, Charsets.UTF_8)
                    val capabilityMissing = advertised.none { it.id == node.id }
                    return@withContext if (capabilityMissing) {
                        context.getString(R.string.companion_status_capability_missing, reply)
                    } else reply
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cause = e.cause ?: e
                    failures.add(cause.javaClass.simpleName)
                }
            }
            val discovery = if (discoveryFailed) context.getString(R.string.companion_status_capability_lookup_failed) else ""
            discovery + context.getString(
                R.string.companion_status_no_reply_with_errors,
                failures.distinct().joinToString()
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cause = e.cause ?: e
            context.getString(R.string.companion_status_check_failed, cause.javaClass.simpleName)
        }
    }

    suspend fun openStore(context: Context): String {
        val helper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
        val links = listOf(
            "market://details?id=${context.packageName}",
            "https://play.google.com/store/apps/details?id=${context.packageName}"
        )
        var failure = context.getString(R.string.companion_status_timeout)
        for (link in links) {
            try {
                val sent = withTimeoutOrNull(15_000L.milliseconds) {
                    helper.startRemoteActivity(
                        Intent(Intent.ACTION_VIEW, link.toUri())
                            .addCategory(Intent.CATEGORY_BROWSABLE), null
                    ).await()
                    true
                } ?: false
                if (sent) {
                    return context.getString(R.string.companion_store_request_sent)
                }
                failure = context.getString(R.string.companion_status_timeout)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = (e.cause ?: e).javaClass.simpleName
            }
        }
        return context.getString(R.string.companion_store_request_failed, failure)
    }
}

