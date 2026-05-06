package org.happyzion.api.adminaccount.application

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder

@Configuration
class AdminAccountPasswordConfig {
    @Bean
    fun adminPasswordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
