package dev.sonora.core.deezer

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Only authenticated full-track Sonora URIs are accepted; there is no preview URL fallback. */
internal class DeezerRoutingDataSourceFactory(
    private val account: DeezerPrivateSession,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = DeezerFullTrackDataSource(account)
}

/** Fetches and decrypts a full track in memory, then serves requested byte ranges. */
private class DeezerFullTrackDataSource(
    private val account: DeezerPrivateSession,
) : DataSource {
    private var source: ByteArray? = null
    private var position = 0
    private var end = 0
    private var uri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        if (dataSpec.uri.scheme != "sonora") throw IOException("Only signed-in Deezer full-track playback is supported.")
        val trackId = dataSpec.uri.lastPathSegment.orEmpty().removeSuffix(".mp3")
        if (trackId.isBlank() || !trackId.all { it.isDigit() || it == '-' }) {
            throw IOException("Invalid Deezer track identifier.")
        }
        val bytes = try {
            runBlocking(Dispatchers.IO) { account.downloadTrack(trackId) }
        } catch (error: Exception) {
            throw IOException(error.message ?: "Could not stream this Deezer track.", error)
        }
        source = bytes
        position = dataSpec.position.coerceAtMost(bytes.size.toLong()).toInt()
        val requestedEnd = if (dataSpec.length == C.LENGTH_UNSET.toLong()) bytes.size.toLong()
            else (dataSpec.position + dataSpec.length).coerceAtMost(bytes.size.toLong())
        end = requestedEnd.coerceAtLeast(position.toLong()).toInt()
        return (end - position).toLong()
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val bytes = source ?: return C.RESULT_END_OF_INPUT
        if (position >= end) return C.RESULT_END_OF_INPUT
        val count = minOf(length, end - position)
        System.arraycopy(bytes, position, buffer, offset, count)
        position += count
        return count
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = emptyMap()

    override fun close() {
        source?.fill(0)
        source = null
        position = 0
        end = 0
        uri = null
    }
}
