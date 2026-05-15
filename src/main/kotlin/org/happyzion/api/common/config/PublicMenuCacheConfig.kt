package org.happyzion.api.common.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Caching
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.time.Duration.parse

object PublicMenuCacheNames {
    const val NAVIGATION = "public-menu-navigation"
    const val RESOLVE = "public-menu-resolve"
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@Caching(
    evict = [
        CacheEvict(cacheNames = [PublicMenuCacheNames.NAVIGATION], allEntries = true),
        CacheEvict(cacheNames = [PublicMenuCacheNames.RESOLVE], allEntries = true),
    ],
)
annotation class EvictPublicMenuCache

@Configuration
@EnableCaching
class PublicMenuCacheConfig {

    @Bean
    fun cacheManager(
        @Value("\${app.cache.public-menu.ttl:PT10M}") ttlString: String,
    ): CacheManager {
        val ttl: Duration = parse(ttlString)
        val manager = CaffeineCacheManager(
            PublicMenuCacheNames.NAVIGATION,
            PublicMenuCacheNames.RESOLVE,
        )
        manager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(ttl),
        )
        return TransactionAwareCacheManagerProxy(manager)
    }
}
