package se.samuel.analytics.notion.sync.internal.network

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.encodeToString
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal suspend fun Call.awaitResponse(): Response = try {
    suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                continuation.resume(response)
            }
        })
    }
} catch (t: Throwable) {
    cancel()
    throw t
}

internal inline fun <reified T> T.toApplicationJsonRequestBody(): RequestBody =
    JsonFormat
        .encodeToString(this)
        .toRequestBody("application/json".toMediaType())