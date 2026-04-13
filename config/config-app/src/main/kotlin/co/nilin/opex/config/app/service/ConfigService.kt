package co.nilin.opex.config.app.service

import co.nilin.opex.config.app.data.UserConfig
import co.nilin.opex.config.app.data.UserConfigRequest
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.ReactiveRedisTemplate
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono

@Service
class ConfigService(
    private val redisTemplate: ReactiveRedisTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private fun userKey(userId: String) = "config:user:$userId"

    fun getUserConfig(userId: String): Mono<UserConfig> {
        return redisTemplate.opsForValue().get(userKey(userId))
            .map { objectMapper.readValue(it, UserConfig::class.java) }
            .defaultIfEmpty(UserConfig())
    }

    fun saveUserConfig(userId: String, request: UserConfigRequest): Mono<UserConfig> {
        return getUserConfig(userId).flatMap { existing ->
            val updated = existing.copy(
                theme = request.theme ?: existing.theme,
                language = request.language ?: existing.language,
                favoritePairs = request.favoritePairs ?: existing.favoritePairs
            )
            val json = objectMapper.writeValueAsString(updated)
            redisTemplate.opsForValue().set(userKey(userId), json)
                .thenReturn(updated)
        }
    }
}
