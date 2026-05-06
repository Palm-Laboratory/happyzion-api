package org.happyzion.api.board.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.board.application.UploadAssetService
import org.happyzion.api.board.application.UploadTokenService
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.common.config.AdminProperties
import org.happyzion.api.common.error.ForbiddenException
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/v1/admin/uploads")
class UploadAdminController(
    private val uploadTokenService: UploadTokenService,
    private val uploadAssetService: UploadAssetService,
    private val adminProperties: AdminProperties,
) {
    @PostMapping("/token")
    fun issueToken(
        @RequestHeader("X-Admin-Key", required = false) adminKey: String?,
        @RequestHeader("X-Admin-Actor-Id") actorId: Long,
        @Valid @RequestBody request: UploadTokenIssueRequest,
    ): UploadTokenIssueResponse {
        validateAdminKey(adminKey)

        val result = uploadTokenService.issueToken(
            actorId = actorId,
            kind = request.kind,
            maxByteSize = request.maxByteSize,
            allowedMimeTypes = request.allowedMimeTypes,
        )

        return UploadTokenIssueResponse(rawToken = result.rawToken)
    }

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(
        @RequestHeader(name = "X-Upload-Token") rawToken: String,
        @RequestParam("file") file: MultipartFile,
        @RequestParam kind: PostAssetKind,
    ): UploadAssetResponse {
        val result = uploadAssetService.upload(
            rawToken = rawToken,
            file = file,
            kind = kind,
        )

        return UploadAssetResponse(
            assetId = result.assetId,
            storedPath = result.storedPath,
            mimeType = result.mimeType,
            byteSize = result.byteSize,
            width = result.width,
            height = result.height,
        )
    }

    private fun validateAdminKey(adminKey: String?) {
        val configuredKey = adminProperties.syncKey.trim()
        if (configuredKey.isBlank()) {
            throw IllegalStateException("ADMIN_SYNC_KEY is not configured.")
        }

        if (adminKey.isNullOrBlank() || adminKey != configuredKey) {
            throw ForbiddenException("관리자 키가 올바르지 않습니다.")
        }
    }
}

data class UploadTokenIssueRequest(
    val kind: PostAssetKind,
    val maxByteSize: Long,
    val allowedMimeTypes: List<String>,
)

data class UploadTokenIssueResponse(
    val rawToken: String,
)

data class UploadAssetResponse(
    val assetId: Long,
    val storedPath: String,
    val mimeType: String?,
    val byteSize: Long,
    val width: Int?,
    val height: Int?,
)
