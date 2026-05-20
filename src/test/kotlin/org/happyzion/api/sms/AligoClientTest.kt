package org.happyzion.api.sms

import org.assertj.core.api.Assertions.assertThat
import org.hamcrest.Matchers.containsString
import org.happyzion.api.sms.infrastructure.client.AligoApiException
import org.happyzion.api.sms.infrastructure.client.AligoClient
import org.happyzion.api.sms.infrastructure.client.AligoProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient

/**
 * Unit-level tests for AligoClient using MockRestServiceServer.
 *
 * Strategy: create a fresh RestClient.Builder per test in setUp(), bind the mock
 * server to it, then build the RestClient and AligoClient with that intercepted
 * builder. This ensures MockRestServiceServer intercepts every request.
 */
@SpringBootTest(classes = [AligoTestConfig::class])
@TestPropertySource(
    properties = [
        "happyzion.aligo.base-url=https://apis.aligo.in",
        "happyzion.aligo.user-id=test-user",
        "happyzion.aligo.api-key=test-key",
        "happyzion.aligo.sender=01012345678",
        "happyzion.aligo.testmode=true",
    ]
)
class AligoClientTest {

    @Autowired
    lateinit var aligoProperties: AligoProperties

    private lateinit var server: MockRestServiceServer
    private lateinit var aligoClient: AligoClient

    @BeforeEach
    fun setUp() {
        val builder = RestClient.builder().baseUrl(aligoProperties.baseUrl)
        server = MockRestServiceServer.bindTo(builder).build()
        aligoClient = AligoClient(builder.build(), aligoProperties)
    }

    // ─── /send/ ──────────────────────────────────────────────────────────────

    @Test
    fun `send posts to send endpoint with multipart content type`() {
        server.expect(requestTo("https://apis.aligo.in/send/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(containsString("testmode_yn")))
            .andRespond(
                withSuccess(
                    """{"result_code":1,"message":"ok","msg_id":"12345","success_cnt":1,"error_cnt":0}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = aligoClient.send(
            mapOf("msg" to "Hello", "receiver" to "01012345678", "sender" to "01012345678")
        )

        assertThat(result.resultCode).isEqualTo(1)
        assertThat(result.msgId).isEqualTo("12345")
        assertThat(result.successCnt).isEqualTo(1)
        assertThat(result.errorCnt).isEqualTo(0)
        server.verify()
    }

    @Test
    fun `send throws AligoApiException when result_code is not 1`() {
        server.expect(requestTo("https://apis.aligo.in/send/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andRespond(
                withSuccess(
                    """{"result_code":-1,"message":"인증 실패"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val ex = assertThrows<AligoApiException> {
            aligoClient.send(mapOf("msg" to "Hello", "receiver" to "01012345678"))
        }

        assertThat(ex.resultCode).isEqualTo(-1)
        assertThat(ex.message).contains("인증 실패")
        server.verify()
    }

    // ─── /send_mass/ ─────────────────────────────────────────────────────────

    @Test
    fun `sendMass posts to send_mass endpoint with multipart content type`() {
        server.expect(requestTo("https://apis.aligo.in/send_mass/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andExpect(content().string(containsString("testmode_yn")))
            .andRespond(
                withSuccess(
                    """{"result_code":1,"message":"ok","msg_id":"99999","success_cnt":2,"error_cnt":0}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = aligoClient.sendMass(
            mapOf(
                "cnt" to "2",
                "rec_1" to "01011111111",
                "msg_1" to "안녕하세요 홍길동님",
                "rec_2" to "01022222222",
                "msg_2" to "안녕하세요 김철수님",
            )
        )

        assertThat(result.resultCode).isEqualTo(1)
        assertThat(result.msgId).isEqualTo("99999")
        assertThat(result.successCnt).isEqualTo(2)
        server.verify()
    }

    @Test
    fun `sendMass throws AligoApiException when result_code is not 1`() {
        server.expect(requestTo("https://apis.aligo.in/send_mass/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
            .andRespond(
                withSuccess(
                    """{"result_code":-2,"message":"발신번호 오류"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val ex = assertThrows<AligoApiException> {
            aligoClient.sendMass(mapOf("cnt" to "1", "rec_1" to "01011111111", "msg_1" to "test"))
        }

        assertThat(ex.resultCode).isEqualTo(-2)
        assertThat(ex.message).contains("발신번호 오류")
        server.verify()
    }

    // ─── /list/ ──────────────────────────────────────────────────────────────

    @Test
    fun `list posts to list endpoint with form-urlencoded content type`() {
        server.expect(requestTo("https://apis.aligo.in/list/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(
                withSuccess(
                    """{"result_code":1,"message":"ok","list":[{"mid":"12345","sender":"01012345678"}]}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = aligoClient.list(page = 1, pageSize = 20, startDate = null, limitDay = 7)

        assertThat(result.resultCode).isEqualTo(1)
        assertThat(result.list).hasSize(1)
        assertThat(result.list[0]["mid"]).isEqualTo("12345")
        server.verify()
    }

    @Test
    fun `list throws AligoApiException when result_code is not 1`() {
        server.expect(requestTo("https://apis.aligo.in/list/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(
                withSuccess(
                    """{"result_code":-1,"message":"인증 실패"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val ex = assertThrows<AligoApiException> {
            aligoClient.list(page = 1, pageSize = 20, startDate = null, limitDay = 7)
        }

        assertThat(ex.resultCode).isEqualTo(-1)
        server.verify()
    }

    // ─── /sms_list/ ──────────────────────────────────────────────────────────

    @Test
    fun `smsList posts to sms_list endpoint with form-urlencoded content type`() {
        server.expect(requestTo("https://apis.aligo.in/sms_list/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(
                withSuccess(
                    """{"result_code":1,"message":"ok","list":[{"mid":"12345","sms_state":"success","rphone":"010-1234-5678"}]}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val result = aligoClient.smsList(mid = "12345", page = 1, pageSize = 20)

        assertThat(result.resultCode).isEqualTo(1)
        assertThat(result.list).hasSize(1)
        assertThat(result.list[0].mid).isEqualTo("12345")
        assertThat(result.list[0].smsState).isEqualTo("success")
        assertThat(result.list[0].rphone).isEqualTo("010-1234-5678")
        server.verify()
    }

    @Test
    fun `smsList throws AligoApiException when result_code is not 1`() {
        server.expect(requestTo("https://apis.aligo.in/sms_list/"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
            .andRespond(
                withSuccess(
                    """{"result_code":-1,"message":"인증 실패"}""",
                    MediaType.APPLICATION_JSON,
                )
            )

        val ex = assertThrows<AligoApiException> {
            aligoClient.smsList(mid = "12345", page = 1, pageSize = 20)
        }

        assertThat(ex.resultCode).isEqualTo(-1)
        server.verify()
    }
}

// ─── Test Spring configuration ────────────────────────────────────────────────

@Configuration
@EnableAutoConfiguration(
    exclude = [
        DataSourceAutoConfiguration::class,
        DataSourceTransactionManagerAutoConfiguration::class,
        HibernateJpaAutoConfiguration::class,
        JpaRepositoriesAutoConfiguration::class,
        FlywayAutoConfiguration::class,
    ]
)
@EnableConfigurationProperties(AligoProperties::class)
class AligoTestConfig
