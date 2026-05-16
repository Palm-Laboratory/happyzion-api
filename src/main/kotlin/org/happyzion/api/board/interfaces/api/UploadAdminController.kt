package org.happyzion.api.board.interfaces.api

import jakarta.validation.Valid
import org.happyzion.api.board.application.UploadAssetService
import org.happyzion.api.board.application.UploadTokenService
import org.happyzion.api.board.domain.PostAssetKind
import org.happyzion.api.common.security.AdminKeyRequired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestAttribute
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
) {
    @AdminKeyRequired
    @PostMapping("/token")
    fun issueToken(
        @RequestAttribute("adminAccountId") actorId: Long,
        @Valid @RequestBody request: UploadTokenIssueRequest,
    ): UploadTokenIssueResponse {
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
