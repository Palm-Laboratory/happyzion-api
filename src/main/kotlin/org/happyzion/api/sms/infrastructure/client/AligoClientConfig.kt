package org.happyzion.api.sms.infrastructure.client

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

@Configuration
class AligoClientConfig {

    @Bean
    fun aligoClient(builder: RestClient.Builder, properties: AligoProperties): AligoClient {
        val factory = SimpleClientHttpRequestFactory()
        factory.setConnectTimeout(properties.connectTimeoutMs.toInt())
        factory.setReadTimeout(properties.readTimeoutMs.toInt())

        val restClient = builder
            .baseUrl(properties.baseUrl)
            .requestFactory(factory)
            .build()

        return AligoClient(restClient, properties)
    }
}
