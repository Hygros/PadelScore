package ch.hygro.padelscore.health

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.concurrent.futures.await
import androidx.wear.remote.interactions.RemoteActivityHelper
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

internal object CompanionSetup {
    suspend fun status(context: Context): String = withContext(Dispatchers.IO) {
        try {
            val capability = Tasks.await(Wearable.getCapabilityClient(context).getCapability(
                WorkoutSyncProtocol.RECEIVER, CapabilityClient.FILTER_REACHABLE), 10, TimeUnit.SECONDS)
            val node = capability.nodes.firstOrNull()
                ?: return@withContext "Companion not reachable. Connect your phone or install the optional app."
            val bytes = Tasks.await(Wearable.getMessageClient(context).sendRequest(
                node.id, WorkoutSyncProtocol.STATUS, byteArrayOf()), 10, TimeUnit.SECONDS)
            require(bytes.size <= 4096)
            String(bytes, Charsets.UTF_8)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Phone status unavailable. Open the updated companion on your phone."
        }
    }

    suspend fun openStore(context: Context): String {
        return try {
            RemoteActivityHelper(context, ContextCompatExecutor(context)).startRemoteActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + context.packageName))
                    .addCategory(Intent.CATEGORY_BROWSABLE), null
            ).await()
            "Store link sent. Complete installation on your phone. Availability depends on the Play test track."
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            "Could not open phone store. Search for Padel Score on your phone. The companion is optional."
        }
    }

    private fun ContextCompatExecutor(context: Context): java.util.concurrent.Executor =
        androidx.core.content.ContextCompat.getMainExecutor(context)
}
