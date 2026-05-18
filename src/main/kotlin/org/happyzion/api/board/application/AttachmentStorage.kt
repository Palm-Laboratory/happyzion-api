package org.happyzion.api.board.application

import org.happyzion.api.board.domain.PostAssetKind
import org.springframework.core.io.Resource
import org.springframework.web.multipart.MultipartFile

interface AttachmentStorage {
    fun store(
        file: MultipartFile,
        kind: PostAssetKind,
        maxByteSize: Long,
    ): StoredAttachment

    fun delete(storedPath: String)
    fun load(storedPath: String): Resource
}

data class StoredAttachment(
    val storedPath: String,
    val mimeType: String,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
)
