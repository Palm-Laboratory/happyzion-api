package org.happyzion.api.member.interfaces.api

import org.assertj.core.api.Assertions.assertThat
import org.happyzion.api.member.application.ChurchMemberPhotoStreamer
import org.happyzion.api.member.application.StreamedPhoto
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.core.io.Resource
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus

class ChurchMemberPhotoControllerTest {

    private val streamer: ChurchMemberPhotoStreamer = mock()
    private val controller = ChurchMemberPhotoController(streamer)

    @Test
    fun `photo streaming returns 200 with correct content type and no-store cache`() {
        val resource: Resource = mock()
        whenever(streamer.load(3L, 42L)).thenReturn(StreamedPhoto(resource, "image/jpeg"))

        val response = controller.get(actorId = 42L, id = 3L)

        assertThat(response.statusCode).isEqualTo(HttpStatus.OK)
        assertThat(response.headers.contentType?.toString()).isEqualTo("image/jpeg")
        val cacheControl = response.headers[HttpHeaders.CACHE_CONTROL]?.firstOrNull()
        assertThat(cacheControl).contains("no-store")
        assertThat(response.body).isSameAs(resource)
    }
}
