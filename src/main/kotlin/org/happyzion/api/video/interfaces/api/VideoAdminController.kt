package org.happyzion.api.video.interfaces.api

import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.video.application.VideoService
import org.happyzion.api.video.interfaces.dto.AdminVideoListResponse
import org.happyzion.api.video.interfaces.dto.UpdateVideoMetaRequest
import org.happyzion.api.video.interfaces.dto.toCommand
import org.happyzion.api.video.interfaces.dto.toDto
import org.happyzion.api.youtube.domain.YouTubeContentForm
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/videos")
class VideoAdminController(
    private val videoService: VideoService,
) {
    @GetMapping
    fun getVideos(
        @RequestParam(required = false) form: YouTubeContentForm?,
        @RequestParam(required = false) menuId: Long?,
    ): AdminVideoListResponse {
        return AdminVideoListResponse(
            items = if (menuId != null) {
                videoService.getAdminVideosByMenu(menuId).map { it.toDto() }
            } else {
                videoService.getAdminVideos(form).map { it.toDto() }
            },
        )
    }

    @GetMapping("/{videoId}")
    fun getVideoDetail(
        @PathVariable videoId: String,
    ) = videoService.getAdminVideoDetail(videoId).toDto()

    @PutMapping("/{videoId}")
    fun updateVideoMeta(
        @PathVariable videoId: String,
        @RequestBody request: UpdateVideoMetaRequest,
    ) = videoService.updateAdminVideoMeta(videoId, request.toCommand()).toDto()
}
