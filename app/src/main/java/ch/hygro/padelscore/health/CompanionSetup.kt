package ch.hygro.padelscore.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.core.content.ContextCompat
import androidx.wear.remote.interactions.RemoteActivityHelper
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
                return@withContext "No reachable device. Check phone/watch pairing and Google Play services."
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
                        "$reply\nCompanion replied, but capability discovery is missing. Update both apps."
                    } else reply
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    val cause = e.cause ?: e
                    failures.add(cause.javaClass.simpleName)
                }
            }
            val discovery = if (discoveryFailed) "Capability lookup failed. " else ""
            discovery + "Device reachable, but no companion status reply. " +
                "Open the updated phone app. Check that both apps use the same signing certificate " +
                "and installation source. Errors: ${failures.distinct().joinToString()}."
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val cause = e.cause ?: e
            "Phone connection check failed (${cause.javaClass.simpleName}). " +
                "Check pairing and Google Play services."
        }
    }

    suspend fun openStore(context: Context): String {
        val helper = RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
        val links = listOf(
            "market://details?id=${context.packageName}",
            "https://play.google.com/store/apps/details?id=${context.packageName}"
        )
        var failure = "Timeout"
        for (link in links) {
            try {
                val sent = withTimeoutOrNull(15_000L) {
                    helper.startRemoteActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(link))
                            .addCategory(Intent.CATEGORY_BROWSABLE), null
                    ).await()
                    true
                } ?: false
                if (sent) {
                    return "Store request sent. Unlock your phone and confirm installation there. " +
                        "This does not confirm installation or test-track access. " +
                        "Nothing opens on a Xiaomi, Redmi or POCO phone? " +
                        "In phone Settings, open your watch plugin's app permissions and, if available, " +
                        "allow background pop-up windows. For Samsung watches, change the watch plugin, " +
                        "not PadelScore. Menu names vary."
                }
                failure = "Timeout"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = (e.cause ?: e).javaClass.simpleName
            }
        }
        return "Phone store request failed ($failure). Unlock your phone and check the watch connection. " +
            "Alternatively, open Play Store on the phone and search for Padel Score."
    }
}
