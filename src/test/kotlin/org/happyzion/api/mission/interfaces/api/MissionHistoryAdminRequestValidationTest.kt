package org.happyzion.api.mission.interfaces.api

import jakarta.validation.Validation
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MissionHistoryAdminRequestValidationTest {

    private val validator = Validation.buildDefaultValidatorFactory().validator

    @Test
    fun `create request rejects invalid year caption tone and nested entries`() {
        val request = MissionYearCreateRequest(
            year = "",
            caption = "x".repeat(201),
            tone = "purple",
            entries = listOf(
                MissionEntryRequest(
                    month = "Foo",
                    place = "",
                )
            ),
        )

        val messages = validator.validate(request).map { it.message }.toSet()

        assertThat(messages).contains(
            "연도를 입력해 주세요.",
            "캡션은 200자 이내로 입력해 주세요.",
            "색상은 gold 또는 red만 사용할 수 있습니다.",
            "월은 Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec 중 하나여야 합니다.",
            "나라/지역명을 입력해 주세요.",
        )
    }

    @Test
    fun `update request accepts valid mission history payload`() {
        val request = MissionYearUpdateRequest(
            year = "2026",
            caption = "선교는 계속됩니다",
            tone = "gold",
            entries = listOf(
                MissionEntryRequest(
                    month = "May",
                    place = "필리핀 팡가시난",
                    isFirst = true,
                )
            ),
        )

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun `entry month may be omitted when the month should not be displayed`() {
        val request = MissionYearCreateRequest(
            year = "2026",
            caption = "선교는 계속됩니다",
            entries = listOf(
                MissionEntryRequest(
                    month = null,
                    place = "필리핀 팡가시난",
                )
            ),
        )

        assertThat(validator.validate(request)).isEmpty()
    }

    @Test
    fun `batch save request validates every nested year and entry before service execution`() {
        val request = MissionYearBatchSaveRequest(
            years = listOf(
                MissionYearBatchUpdateRequest(
                    id = 0,
                    year = "",
                    caption = "",
                    tone = "purple",
                    entries = listOf(
                        MissionEntryRequest(
                            month = "Foo",
                            place = "",
                        )
                    ),
                )
            )
        )

        val messages = validator.validate(request).map { it.message }.toSet()

        assertThat(messages).contains(
            "연도 id가 올바르지 않습니다.",
            "연도를 입력해 주세요.",
            "캡션을 입력해 주세요.",
            "색상은 gold 또는 red만 사용할 수 있습니다.",
            "월은 Jan, Feb, Mar, Apr, May, Jun, Jul, Aug, Sep, Oct, Nov, Dec 중 하나여야 합니다.",
            "나라/지역명을 입력해 주세요.",
        )
    }
}
