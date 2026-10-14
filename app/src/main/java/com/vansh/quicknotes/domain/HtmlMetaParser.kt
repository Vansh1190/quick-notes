package com.vansh.quicknotes.domain

import kotlinx.serialization.Serializable

@Serializable
data class LinkMetadata(
    val url: String,
    val title: String? = null,
    val siteName: String? = null,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    /** visible text of the page, trimmed; fuel for Link Essence */
    val rawText: String? = null,
)

/**
 * Minimal HTML metadata extractor (no HTML lib): pulls <title>, og:title/og:site_name/
 * og:image/description and the <title> tag. Tolerant of attribute order and self-closing.
 */
object HtmlMetaParser {

    private val titleTag = Regex("(?is)<title[^>]*>(.*?)</title>")
    private val metaTag = Regex("(?is)<meta\\b[^>]*>")

    fun parse(html: String, baseUrl: String): LinkMetadata {
        val metas = metaTag.findAll(html).map { attrsOf(it.value) }.toList()
        fun meta(vararg names: String): String? {
            for (name in names) {
                val hit = metas.firstOrNull {
                    (it["property"]?.equals(name, true) == true) || (it["name"]?.equals(name, true) == true)
                }
                hit?.get("content")?.takeIf { it.isNotBlank() }?.let { return decodeEntities(it) }
            }
            return null
        }
        val title = meta("og:title") ?: titleTag.find(html)?.groupValues?.get(1)?.trim()?.let { decodeEntities(it) }
        val site = meta("og:site_name") ?: hostOf(baseUrl)
        val image = meta("og:image", "twitter:image")?.let { resolve(it, baseUrl) }
        val desc = meta("og:description", "description")
        return LinkMetadata(
            url = baseUrl,
            title = title?.takeIf { it.isNotBlank() },
            siteName = site,
            description = desc,
            thumbnailUrl = image,
            rawText = extractText(html)?.take(12_000),
        )
    }

    private val scriptStyle = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val tags = Regex("(?is)<[^>]+>")

    fun extractText(html: String): String? =
        html.replace(scriptStyle, " ").replace(Regex("(?is)<br[^>]*>|</p>|</div>"), "\n")
            .replace(tags, " ").replace(Regex("\\s+"), " ").trim()
            .takeIf { it.length > 40 }

    private fun attrsOf(tag: String): Map<String, String> {
        val out = HashMap<String, String>()
        val attr = Regex("(?is)([a-zA-Z-]+)\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
        for (m in attr.findAll(tag)) {
            val key = m.groupValues[1].lowercase()
            val value = (m.groupValues[3].ifEmpty { m.groupValues[4] })
            out[key] = value
        }
        return out
    }

    private fun hostOf(url: String): String? = runCatching {
        java.net.URI(url).host?.removePrefix("www.")
    }.getOrNull()

    private fun resolve(href: String, base: String): String = runCatching {
        java.net.URI(base).resolve(href).toString()
    }.getOrDefault(href)

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
}
