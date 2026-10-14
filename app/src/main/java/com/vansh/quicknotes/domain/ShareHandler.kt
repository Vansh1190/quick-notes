package com.vansh.quicknotes.domain

import javax.inject.Inject
import javax.inject.Singleton

/** What another app shared into Quick Notes (spec §5.5 / §10). */
sealed class SharedContent {
    data class Text(val text: String) : SharedContent()
    data class Link(val url: String) : SharedContent()
    data class Image(val bytes: ByteArray, val mimeType: String) : SharedContent()
}

/** Pure decision logic extracted from Intent plumbing so it is unit-testable. */
object ShareParser {

    private val urlOnly = Regex("^\\s*(https?://\\S+)\\s*$")

    fun parse(textType: Boolean, imageBytes: Pair<ByteArray, String>?, extraText: String?): SharedContent? = when {
        imageBytes != null -> SharedContent.Image(imageBytes.first, imageBytes.second)
        textType -> {
            val text = extraText?.trim().orEmpty()
            when {
                text.isEmpty() -> null
                urlOnly.matches(text) -> SharedContent.Link(urlOnly.find(text)!!.groupValues[1])
                else -> SharedContent.Text(text)
            }
        }
        else -> null
    }
}

/** Creates a note from shared content (capture-first: no dialogs in the way). */
@Singleton
class ShareHandler @Inject constructor(
    private val repository: com.vansh.quicknotes.data.repository.NotesRepository,
    private val mediaStore: com.vansh.quicknotes.data.media.MediaStore,
    private val linkFetcher: LinkFetcher,
) {
    suspend fun handle(content: SharedContent): String = when (content) {
        is SharedContent.Text -> repository.createNote(content.text)

        is SharedContent.Link -> {
            val id = repository.createNote()
            val note = repository.loadNote(id)!!
            val blockId = java.util.UUID.randomUUID().toString()
            repository.saveNote(
                note.copy(
                    blocks = note.blocks + com.vansh.quicknotes.domain.model.Block(
                        id = blockId,
                        type = com.vansh.quicknotes.domain.model.BlockType.LINK,
                        orderIndex = note.blocks.size,
                        link = com.vansh.quicknotes.domain.model.LinkPayload(content.url),
                    ),
                ),
            )
            val meta = linkFetcher.fetch(content.url)
            repository.loadNote(id)?.let { fresh ->
                repository.saveNote(
                    fresh.copy(
                        blocks = fresh.blocks.map {
                            if (it.id == blockId && it.link != null) {
                                it.copy(link = it.link.copy(title = meta.title, siteName = meta.siteName, fetched = true))
                            } else {
                                it
                            }
                        },
                    ),
                )
            }
            id
        }

        is SharedContent.Image -> {
            val id = repository.createNote()
            val asset = mediaStore.saveImage(content.bytes, id, if (content.mimeType.contains("png")) "png" else "jpg")
            val note = repository.loadNote(id)!!
            repository.saveNote(
                note.copy(
                    blocks = note.blocks + com.vansh.quicknotes.domain.model.Block(
                        id = java.util.UUID.randomUUID().toString(),
                        type = com.vansh.quicknotes.domain.model.BlockType.IMAGE,
                        orderIndex = note.blocks.size,
                        image = com.vansh.quicknotes.domain.model.ImagePayload(asset.id, asset.width ?: 0, asset.height ?: 0),
                    ),
                ),
            )
            id
        }
    }
}

/** Handed from MainActivity's intent handling to the nav host. */
@Singleton
class ShareRouter @Inject constructor() {
    private val _requests = kotlinx.coroutines.flow.MutableSharedFlow<SharedContent>(extraBufferCapacity = 8)
    val requests: kotlinx.coroutines.flow.SharedFlow<SharedContent> = _requests

    private val _openNote = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8)
    val openNote: kotlinx.coroutines.flow.SharedFlow<String> = _openNote

    fun submit(content: SharedContent) {
        _requests.tryEmit(content)
    }

    fun noteOpened(id: String) {
        _openNote.tryEmit(id)
    }
}
