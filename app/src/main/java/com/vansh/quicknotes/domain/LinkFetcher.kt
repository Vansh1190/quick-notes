package com.vansh.quicknotes.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fetches link cards (spec §5.5). Plain HttpURLConnection (no extra deps); any failure
 * returns a plain-URL fallback card so offline never blocks the note.
 */
@Singleton
class LinkFetcher @Inject constructor() {

    suspend fun fetch(url: String): LinkMetadata = withContext(Dispatchers.IO) {
        runCatching { fetchBlocking(url) }.getOrElse { LinkMetadata(url = url, siteName = null) }
    }

    private fun fetchBlocking(url: String): LinkMetadata {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 5_000
            conn.readTimeout = 5_000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "QuickNotes/1.0 (+link card preview)")
            val contentType = conn.contentType.orEmpty()
            if (conn.responseCode !in 200..299 || !contentType.contains("html")) {
                return LinkMetadata(url = url, siteName = HtmlMetaParser.parse("", conn.url.toString()).siteName)
            }
            val body = conn.inputStream.bufferedReader().use { reader ->
                val buf = CharArray(64 * 1024)
                val sb = StringBuilder()
                var read = reader.read(buf)
                while (read > 0 && sb.length < 256 * 1024) {
                    sb.append(buf, 0, read)
                    read = reader.read(buf)
                }
                sb.toString()
            }
            HtmlMetaParser.parse(body, conn.url.toString())
        } finally {
            conn.disconnect()
        }
    }
}
