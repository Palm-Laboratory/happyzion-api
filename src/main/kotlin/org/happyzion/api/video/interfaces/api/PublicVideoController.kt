package org.happyzion.api.video.interfaces.api

import org.happyzion.api.video.application.VideoService
import org.happyzion.api.video.interfaces.dto.toDto
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/public/videos")
class PublicVideoController(
    private val videoService: VideoService,
) {
    @GetMapping("/items")
    fun getPlaylistVideosByPath(
        @RequestParam path: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "6") size: Int,
    ) = videoService.getPublicPlaylistVideosByPath(path, page, size).toDto()

    @GetMapping("/detail")
    fun getPlaylistVideoDetailByPath(
        @RequestParam path: String,
        @RequestParam videoId: String,
    ) = videoService.getPublicPlaylistVideoDetailByPath(path, videoId).toDto()

}
