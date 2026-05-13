package org.happyzion.api.common.error

import org.apache.catalina.connector.ClientAbortException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.io.IOException

class GlobalExceptionHandlerTest {

    private val handler = GlobalExceptionHandler()

    @Test
    fun `client abort does not write an error response body`() {
        val response = handler.handleClientAbort(ClientAbortException(IOException("Broken pipe")))

        assertThat(response.statusCode.value()).isEqualTo(499)
        assertThat(response.body).isNull()
        assertThat(response.headers.contentType).isNull()
    }

    @Test
    fun `internal errors are returned as json responses`() {
        val response = handler.handleInternal(RuntimeException("database password leaked"))

        assertThat(response.statusCode.value()).isEqualTo(500)
        assertThat(response.headers.contentType).isEqualTo(MediaType.APPLICATION_JSON)
        assertThat(response.body?.code).isEqualTo("INTERNAL_SERVER_ERROR")
        assertThat(response.body?.message).isEqualTo("서버 오류가 발생했습니다.")
    }
}
