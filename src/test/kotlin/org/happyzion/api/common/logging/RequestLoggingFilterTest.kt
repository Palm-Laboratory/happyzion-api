package org.happyzion.api.common.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class RequestLoggingFilterTest {

    private val filter = RequestLoggingFilter()

    @Test
    fun `redacts name and phone in members path`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=김철수&phone=01012345678&page=0",
        )
        assertThat(out).contains("name=[REDACTED]")
        assertThat(out).contains("phone=[REDACTED]")
        assertThat(out).contains("page=0")
        assertThat(out).doesNotContain("김철수")
        assertThat(out).doesNotContain("01012345678")
    }

    @Test
    fun `redaction is case insensitive on key`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "NAME=김철수&Phone=01012345678",
        )
        assertThat(out).doesNotContain("김철수")
        assertThat(out).doesNotContain("01012345678")
    }

    @Test
    fun `redacts duplicate parameters`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=A&name=B",
        )
        assertThat(out).doesNotContain("=A")
        assertThat(out).doesNotContain("=B")
    }

    @Test
    fun `redacts url-encoded values without decoding`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=%EA%B9%80%EC%B2%A0%EC%88%98",
        )
        assertThat(out).doesNotContain("%EA%B9%80")
        assertThat(out).contains("name=[REDACTED]")
    }

    @Test
    fun `empty values stay as-is`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members",
            query = "name=&phone=",
        )
        assertThat(out).contains("name=")
        assertThat(out).contains("phone=")
    }

    @Test
    fun `applies to nested members paths (photo, audit-logs)`() {
        val out1 = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members/123/photo",
            query = "name=김",
        )
        val out2 = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/members/123/audit-logs",
            query = "name=김",
        )
        assertThat(out1).doesNotContain("김")
        assertThat(out2).doesNotContain("김")
    }

    @Test
    fun `does not redact on non-members paths`() {
        val out = filter.buildRequestPathForTesting(
            uri = "/api/v1/admin/boards/notice/posts",
            query = "title=hello&name=admin",
        )
        assertThat(out).contains("title=hello")
        assertThat(out).contains("name=admin")
    }
}
