package org.happyzion.api.sms.infrastructure.client

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.MediaType
import org.springframework.util.LinkedMultiValueMap
import org.springframework.web.client.RestClient

// ─── Response DTOs ───────────────────────────────────────────────────────────

data class AligoSendResult(
    val msgId: String,
    val successCnt: Int,
    val errorCnt: Int,
    val resultCode: Int,
    val message: String,
)

data class AligoListResult(
    val resultCode: Int,
    val message: String,
    val list: List<Map<String, Any?>>,
)

data class AligoSmsListResult(
    val resultCode: Int,
    val message: String,
    val list: List<AligoSmsListItem>,
)

data class AligoSmsListItem(
    val mid: String,
    @JsonProperty("sms_state") val smsState: String?,
    val rphone: String?,
)

// ─── Internal raw response used for deserialization ──────────────────────────

private data class AligoRawSendResponse(
    @JsonProperty("result_code") val resultCode: Int = 0,
    val message: String = "",
    @JsonProperty("msg_id") val msgId: String = "",
    @JsonProperty("success_cnt") val successCnt: Int = 0,
    @JsonProperty("error_cnt") val errorCnt: Int = 0,
)

private data class AligoRawListResponse(
    @JsonProperty("result_code") val resultCode: Int = 0,
    val message: String = "",
    val list: List<Map<String, Any?>> = emptyList(),
)

private data class AligoRawSmsListResponse(
    @JsonProperty("result_code") val resultCode: Int = 0,
    val message: String = "",
    val list: List<AligoSmsListItem> = emptyList(),
)

// ─── Client ──────────────────────────────────────────────────────────────────

class AligoClient(
    private val restClient: RestClient,
    private val properties: AligoProperties,
) {

    /**
     * POST /send/ — single message or same message to many recipients.
     * Content-Type: multipart/form-data
     * Auth fields (user_id, key, testmode_yn) are added automatically.
     */
    fun send(params: Map<String, String>): AligoSendResult {
        val form = buildAuthForm(params)
        val raw = restClient.post()
            .uri("/send/")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(form)
            .retrieve()
            .body(AligoRawSendResponse::class.java)
            ?: throw AligoApiException(0, "Empty response from /send/")
        if (raw.resultCode != 1) throw AligoApiException(raw.resultCode, raw.message)
        return AligoSendResult(
            msgId = raw.msgId,
            successCnt = raw.successCnt,
            errorCnt = raw.errorCnt,
            resultCode = raw.resultCode,
            message = raw.message,
        )
    }

    /**
     * POST /send_mass/ — bulk with per-recipient messages.
     * Content-Type: multipart/form-data
     * Auth fields are added automatically.
     */
    fun sendMass(params: Map<String, String>): AligoSendResult {
        val form = buildAuthForm(params)
        val raw = restClient.post()
            .uri("/send_mass/")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .body(form)
            .retrieve()
            .body(AligoRawSendResponse::class.java)
            ?: throw AligoApiException(0, "Empty response from /send_mass/")
        if (raw.resultCode != 1) throw AligoApiException(raw.resultCode, raw.message)
        return AligoSendResult(
            msgId = raw.msgId,
            successCnt = raw.successCnt,
            errorCnt = raw.errorCnt,
            resultCode = raw.resultCode,
            message = raw.message,
        )
    }

    /**
     * POST /list/ — send history list.
     * Content-Type: application/x-www-form-urlencoded
     */
    fun list(page: Int, pageSize: Int, startDate: String?, limitDay: Int): AligoListResult {
        val form = LinkedMultiValueMap<String, String>()
        form.add("user_id", properties.userId)
        form.add("key", properties.apiKey)
        form.add("page", page.toString())
        form.add("page_size", pageSize.toString())
        if (!startDate.isNullOrBlank()) form.add("start_date", startDate)
        form.add("limit_day", limitDay.toString())

        val raw = restClient.post()
            .uri("/list/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(AligoRawListResponse::class.java)
            ?: throw AligoApiException(0, "Empty response from /list/")
        if (raw.resultCode != 1) throw AligoApiException(raw.resultCode, raw.message)
        return AligoListResult(
            resultCode = raw.resultCode,
            message = raw.message,
            list = raw.list,
        )
    }

    /**
     * POST /sms_list/ — per-recipient detail by msg_id.
     * Content-Type: application/x-www-form-urlencoded
     */
    fun smsList(mid: String, page: Int, pageSize: Int): AligoSmsListResult {
        val form = LinkedMultiValueMap<String, String>()
        form.add("user_id", properties.userId)
        form.add("key", properties.apiKey)
        form.add("mid", mid)
        form.add("page", page.toString())
        form.add("page_size", pageSize.toString())

        val raw = restClient.post()
            .uri("/sms_list/")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(AligoRawSmsListResponse::class.java)
            ?: throw AligoApiException(0, "Empty response from /sms_list/")
        if (raw.resultCode != 1) throw AligoApiException(raw.resultCode, raw.message)
        return AligoSmsListResult(
            resultCode = raw.resultCode,
            message = raw.message,
            list = raw.list,
        )
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private fun buildAuthForm(params: Map<String, String>): LinkedMultiValueMap<String, String> {
        val form = LinkedMultiValueMap<String, String>()
        params.forEach { (k, v) -> form.add(k, v) }
        form.add("user_id", properties.userId)
        form.add("key", properties.apiKey)
        if (properties.testmode) form.add("testmode_yn", "Y")
        return form
    }
}

