package co.nilin.opex.config.app.controller

import co.nilin.opex.config.app.data.UserConfig
import co.nilin.opex.config.app.data.UserConfigRequest
import co.nilin.opex.config.app.data.WebConfig
import co.nilin.opex.config.app.service.ConfigService
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

@RestController
class ConfigController(
    private val configService: ConfigService,
    @Value("\${default_theme:DARK}") private val defaultTheme: String,
    @Value("\${default_language:en}") private val language: String,
    @Value("\${logo_url:}") private val logoUrl: String,
    @Value("\${title:Exchange}") private val title: String,
    @Value("\${description:}") private val description: String,
    @Value("\${support_email:}") private val supportEmail: String,
    @Value("\${base_currency:USDT}") private val baseCurrency: String,
    @Value("\${date_type:Gregorian}") private val dateType: String,
    @Value("\${langs:en}") private val langs: String,
) {

    @GetMapping("/web/v1")
    fun getWebConfig(): Mono<WebConfig> {
        return Mono.just(
            WebConfig(
                defaultTheme = defaultTheme,
                language = language,
                logoUrl = logoUrl,
                title = title,
                description = description,
                supportEmail = supportEmail,
                baseCurrency = baseCurrency,
                dateType = dateType,
                supportedLanguages = langs.split(",")
            )
        )
    }

    @GetMapping("/user/v1")
    fun getUserConfig(@RequestHeader("Authorization") authorization: String): Mono<UserConfig> {
        val userId = extractUserId(authorization)
        return configService.getUserConfig(userId)
    }

    @PostMapping("/user/v1")
    fun saveUserConfig(
        @RequestHeader("Authorization") authorization: String,
        @RequestBody request: UserConfigRequest
    ): Mono<UserConfig> {
        val userId = extractUserId(authorization)
        return configService.saveUserConfig(userId, request)
    }

    private fun extractUserId(authorization: String): String {
        try {
            val token = authorization.removePrefix("Bearer ")
            val payload = token.split(".")[1]
            val decoded = String(java.util.Base64.getUrlDecoder().decode(payload))
            val json = com.fasterxml.jackson.databind.ObjectMapper().readTree(decoded)
            return json.get("sub").asText()
        } catch (e: Exception) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token")
        }
    }
}
