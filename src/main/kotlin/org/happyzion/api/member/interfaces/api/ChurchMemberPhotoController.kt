package org.happyzion.api.member.interfaces.api

import org.happyzion.api.common.security.AdminAuthRequired
import org.happyzion.api.member.application.ChurchMemberPhotoStreamer
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@AdminAuthRequired
@RestController
@RequestMapping("/api/v1/admin/members/{id}/photo")
class ChurchMemberPhotoController(
    private val streamer: ChurchMemberPhotoStreamer,
) {
    @GetMapping
    fun get(
        @RequestAttribute("adminAccountId") actorId: Long,
        @PathVariable id: Long,
    ): ResponseEntity<Resource> {
        val (resource, mime) = streamer.load(id, actorId)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(mime))
            .cacheControl(CacheControl.noStore())
            .body(resource)
    }
}
