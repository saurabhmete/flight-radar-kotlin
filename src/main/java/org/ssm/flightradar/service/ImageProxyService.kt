package org.ssm.flightradar.service

import org.slf4j.LoggerFactory
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam

/**
 * Fetches a remote image, resizes it to fit within (w,h), re-encodes as a small JPEG,
 * and disk-caches the result. Used by the ESP32 display so it can decode JPEGs at
 * scale=1 (JPEGDEC's scaled modes produce coloured-line artifacts at MCU boundaries).
 */
class ImageProxyService(cacheDirPath: String = System.getProperty("java.io.tmpdir") + "/flight-radar-img") {

    private val log = LoggerFactory.getLogger(ImageProxyService::class.java)
    private val cacheDir = File(cacheDirPath).apply { mkdirs() }

    // Hosts we're willing to proxy. Keeps the endpoint from becoming an open redirect/SSRF.
    private val allowedHosts = setOf(
        "www.planespotters.net",
        "tile.planespotters.net",
        "img.planespotters.net",
        "cdn.planespotters.net",
        "upload.wikimedia.org",
        "commons.wikimedia.org",
        "i.scdn.co"
    )

    data class Result(val bytes: ByteArray, val contentType: String = "image/jpeg")

    fun fetch(rawUrl: String, w: Int, h: Int): Result? {
        val decoded = try { URLDecoder.decode(rawUrl, Charsets.UTF_8) } catch (_: Exception) { rawUrl }
        val parsed = try { URL(decoded) } catch (_: Exception) { return null }
        if (parsed.protocol !in setOf("http", "https")) return null
        if (parsed.host !in allowedHosts) {
            log.warn("[img-proxy] reject host {}", parsed.host)
            return null
        }
        val tw = w.coerceIn(16, 1024)
        val th = h.coerceIn(16, 1024)

        val key = sha256("${decoded}|${tw}x${th}")
        val cached = File(cacheDir, "$key.jpg")
        if (cached.exists() && cached.length() > 0) {
            return Result(cached.readBytes())
        }

        val src = downloadImage(parsed) ?: return null
        val resized = resize(src, tw, th)
        val jpeg = encodeJpeg(resized, 0.85f)
        try { cached.writeBytes(jpeg) } catch (e: Exception) { log.warn("[img-proxy] cache write failed: {}", e.message) }
        return Result(jpeg)
    }

    private fun downloadImage(url: URL): BufferedImage? {
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 8_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "flight-radar-kotlin/1.0")
        }
        return try {
            if (conn.responseCode != 200) {
                log.warn("[img-proxy] upstream {} returned {}", url, conn.responseCode)
                null
            } else conn.inputStream.use { ImageIO.read(it) }
        } catch (e: Exception) {
            log.warn("[img-proxy] fetch failed {}: {}", url, e.message)
            null
        } finally { conn.disconnect() }
    }

    private fun resize(src: BufferedImage, maxW: Int, maxH: Int): BufferedImage {
        val sw = src.width
        val sh = src.height
        val scale = minOf(maxW.toDouble() / sw, maxH.toDouble() / sh).coerceAtMost(1.0)
        val dw = maxOf(1, (sw * scale).toInt())
        val dh = maxOf(1, (sh * scale).toInt())
        val dst = BufferedImage(dw, dh, BufferedImage.TYPE_INT_RGB)
        val g = dst.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
        g.drawImage(src, 0, 0, dw, dh, null)
        g.dispose()
        return dst
    }

    private fun encodeJpeg(img: BufferedImage, quality: Float): ByteArray {
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val params = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = quality
        }
        val out = ByteArrayOutputStream()
        ImageIO.createImageOutputStream(out).use { ios ->
            writer.output = ios
            writer.write(null, IIOImage(img, null, null), params)
        }
        writer.dispose()
        return out.toByteArray()
    }

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
