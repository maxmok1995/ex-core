package co.nilin.opex.captcha.app.controller

import co.nilin.opex.captcha.app.service.CaptchaService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CaptchaController(private val captchaService: CaptchaService) {

    @PostMapping("/session")
    fun getCaptcha(): ResponseEntity<ByteArray> {
        val (image, sessionKey, expireTime) = captchaService.generateCaptcha()
        return ResponseEntity.ok()
            .header("captcha-session-key", sessionKey)
            .header("captcha-expire-timestamp", expireTime.toString())
            .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "captcha-session-key, captcha-expire-timestamp")
            .contentType(MediaType.IMAGE_JPEG)
            .body(image)
    }

    @GetMapping("/verify")
    fun verifyCaptcha(@RequestParam proof: String): ResponseEntity<String> {
        // proof format: SessionKey(UUID)-UserAnswer-UserIP
        // UUID format: xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx (has exactly 4 dashes)
        // So we find the 5th dash position to split UUID from the rest
        var dashCount = 0
        var splitIndex = -1
        for (i in proof.indices) {
            if (proof[i] == '-') {
                dashCount++
                if (dashCount == 5) {
                    splitIndex = i
                    break
                }
            }
        }

        if (splitIndex == -1) return ResponseEntity.badRequest().body("Invalid proof format")

        val sessionKey = proof.substring(0, splitIndex)
        val remainder = proof.substring(splitIndex + 1)
        // remainder = Answer-IP, Answer has no dashes so split at first dash
        val answerEndIndex = remainder.indexOf('-')
        val answer = if (answerEndIndex == -1) remainder else remainder.substring(0, answerEndIndex)

        return if (captchaService.verify(sessionKey, answer)) {
            ResponseEntity.ok("OK")
        } else {
            ResponseEntity.badRequest().body("Invalid captcha")
        }
    }
}
