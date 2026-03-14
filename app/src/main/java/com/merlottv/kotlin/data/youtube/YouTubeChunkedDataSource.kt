package com.merlottv.kotlin.data.youtube

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InputStream

/**
 * Custom DataSource that downloads YouTube videos in chunks to avoid throttling.
 *
 * YouTube's googlevideo.com CDN throttles connections that request the full file at once.
 * This DataSource downloads in 10MB chunks using YouTube's native `&range=start-end`
 * URL parameter (NOT HTTP Range headers) to work around throttling.
 *
 * Based on NuvioTV's YoutubeChunkedDataSourceFactory approach.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class YouTubeChunkedDataSource(
    private val okHttpClient: OkHttpClient,
    private val userAgent: String = DEFAULT_USER_AGENT
) : DataSource {

    companion object {
        private const val CHUNK_SIZE = 10L * 1024 * 1024  // 10 MB chunks
        private const val DEFAULT_USER_AGENT =
            "com.google.android.youtube/20.10.35 (Linux; U; Android 14; en_US) gzip"
    }

    private var dataSpec: DataSpec? = null
    private var baseUrl: String? = null
    private var contentLength: Long = C.LENGTH_UNSET.toLong()
    private var currentPosition: Long = 0
    private var currentInputStream: InputStream? = null
    private var currentChunkEnd: Long = 0
    private var bytesRemainingInChunk: Long = 0
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) {
        // No-op — we don't support transfer listeners
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        this.currentPosition = dataSpec.position
        this.baseUrl = cleanUrl(dataSpec.uri.toString())

        // Determine total content length from the first chunk response
        openNextChunk()
        opened = true

        return if (dataSpec.length != C.LENGTH_UNSET.toLong()) {
            dataSpec.length
        } else if (contentLength != C.LENGTH_UNSET.toLong()) {
            contentLength - dataSpec.position
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        // If current chunk is exhausted, open the next one
        if (bytesRemainingInChunk <= 0 || currentInputStream == null) {
            closeCurrentStream()
            if (contentLength != C.LENGTH_UNSET.toLong() && currentPosition >= contentLength) {
                return C.RESULT_END_OF_INPUT
            }
            try {
                openNextChunk()
            } catch (e: IOException) {
                return C.RESULT_END_OF_INPUT
            }
        }

        val bytesToRead = length.toLong().coerceAtMost(bytesRemainingInChunk).toInt()
        val bytesRead = currentInputStream?.read(buffer, offset, bytesToRead) ?: C.RESULT_END_OF_INPUT

        if (bytesRead == -1) {
            // End of this chunk — try the next one
            closeCurrentStream()
            if (contentLength != C.LENGTH_UNSET.toLong() && currentPosition >= contentLength) {
                return C.RESULT_END_OF_INPUT
            }
            return 0 // Return 0 so ExoPlayer calls read() again, which opens the next chunk
        }

        currentPosition += bytesRead
        bytesRemainingInChunk -= bytesRead
        return bytesRead
    }

    override fun getUri(): Uri? = dataSpec?.uri

    override fun close() {
        closeCurrentStream()
        opened = false
        dataSpec = null
        baseUrl = null
        contentLength = C.LENGTH_UNSET.toLong()
        currentPosition = 0
    }

    @Throws(IOException::class)
    private fun openNextChunk() {
        val base = baseUrl ?: throw IOException("No base URL")
        val start = currentPosition
        val end = start + CHUNK_SIZE - 1

        // Append YouTube-style range parameter
        val separator = if (base.contains("?")) "&" else "?"
        val chunkUrl = "${base}${separator}range=${start}-${end}"

        val request = Request.Builder()
            .url(chunkUrl)
            .header("User-Agent", userAgent)
            .build()

        val response = try {
            okHttpClient.newCall(request).execute()
        } catch (e: Exception) {
            throw IOException("Chunk request failed: ${e.message}", e)
        }

        if (!response.isSuccessful) {
            response.close()
            throw IOException("Chunk request returned ${response.code}")
        }

        val responseBody = response.body ?: throw IOException("Empty response body")
        val bodyLength = responseBody.contentLength()

        // Parse Content-Range header to get total size if we don't have it yet
        if (contentLength == C.LENGTH_UNSET.toLong()) {
            val contentRange = response.header("Content-Range")
            if (contentRange != null) {
                // Format: "bytes start-end/total"
                val total = contentRange.substringAfter("/", "").toLongOrNull()
                if (total != null && total > 0) {
                    contentLength = total
                }
            }
            // Fallback: if no Content-Range, try Content-Length of the full resource
            if (contentLength == C.LENGTH_UNSET.toLong() && bodyLength > 0) {
                // This is just the chunk length, not helpful for total
                // We'll keep LENGTH_UNSET and rely on end-of-input
            }
        }

        currentInputStream = responseBody.byteStream()
        currentChunkEnd = if (bodyLength > 0) start + bodyLength else end + 1
        bytesRemainingInChunk = if (bodyLength > 0) bodyLength else CHUNK_SIZE
    }

    private fun closeCurrentStream() {
        try {
            currentInputStream?.close()
        } catch (_: Exception) {}
        currentInputStream = null
        bytesRemainingInChunk = 0
    }

    /**
     * Remove any existing `range=` parameter from the URL so we can add our own.
     */
    private fun cleanUrl(url: String): String {
        return url
            .replace(Regex("[&?]range=[^&]*"), "")
            .replace(Regex("\\?$"), "")  // Clean trailing ? if range was the only param
    }

    /**
     * Factory that creates YouTubeChunkedDataSource instances.
     * Use this for googlevideo.com URLs to avoid YouTube throttling.
     */
    class Factory(
        private val okHttpClient: OkHttpClient,
        private val userAgent: String = DEFAULT_USER_AGENT
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return YouTubeChunkedDataSource(okHttpClient, userAgent)
        }
    }
}
