package co.nilin.opex.captcha.app.service

import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.imageio.ImageIO
import kotlin.random.Random

@Service
class CaptchaService {

    private val sessions = ConcurrentHashMap<String, CaptchaSession>()
    private val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    data class CaptchaSession(
        val answer: String,
        val expireTime: Long
    )

    fun generateCaptcha(): Triple<ByteArray, String, Long> {
        val answer = (1..5).map { chars[Random.nextInt(chars.length)] }.joinToString("")
        val sessionKey = UUID.randomUUID().toString()
        val expireTime = System.currentTimeMillis() + 5 * 60 * 1000 // 5 minutes

        sessions[sessionKey] = CaptchaSession(answer, expireTime)

        val image = generateImage(answer)
        return Triple(image, sessionKey, expireTime)
    }

    fun verify(sessionKey: String, answer: String): Boolean {
        val session = sessions[sessionKey] ?: return false
        if (System.currentTimeMillis() > session.expireTime) {
            sessions.remove(sessionKey)
            return false
        }
        val isValid = session.answer.equals(answer, ignoreCase = true)
        sessions.remove(sessionKey)
        return isValid
    }

    private fun generateImage(text: String): ByteArray {
        val width = 150
        val height = 50
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(240, 240, 240)
        g.fillRect(0, 0, width, height)

        // Draw noise lines
        g.color = Color(180, 180, 180)
        repeat(5) {
            g.drawLine(
                Random.nextInt(width), Random.nextInt(height),
                Random.nextInt(width), Random.nextInt(height)
            )
        }

        // Draw text
        val font = Font("Arial", Font.BOLD, 28)
        g.font = font
        text.forEachIndexed { i, c ->
            g.color = Color(Random.nextInt(100), Random.nextInt(100), Random.nextInt(150))
            g.drawString(c.toString(), 15 + i * 25, 35 + Random.nextInt(5))
        }

        // Draw noise dots
        repeat(50) {
            g.color = Color(Random.nextInt(255), Random.nextInt(255), Random.nextInt(255))
            g.fillOval(Random.nextInt(width), Random.nextInt(height), 2, 2)
        }

        g.dispose()

        val baos = ByteArrayOutputStream()
        ImageIO.write(image, "jpeg", baos)
        return baos.toByteArray()
    }

    @Scheduled(fixedDelay = 60000)
    fun cleanExpiredSessions() {
        val now = System.currentTimeMillis()
        sessions.entries.removeIf { it.value.expireTime < now }
    }
}
