package pl.prodevcode.homekit.pairing

import java.security.MessageDigest
import java.util.Base64

/** HAP setup payload (HAP 4.2.1.2): the `X-HM://` URI the Home app scans and the matching `sh` TXT value. */
object SetupPayload {

    private const val FLAG_IP = 2L

    /** [setupCode] as `XXX-XX-XXX`, [setupId] 4 chars `[0-9A-Z]`, [category] e.g. 31 for Television. */
    fun uri(setupCode: String, setupId: String, category: Int): String {
        val code = setupCode.filter { it.isDigit() }.toLong()
        val payload = (category.toLong() shl 31) or (FLAG_IP shl 27) or code
        val encoded = payload.toString(36).uppercase().padStart(9, '0')
        return "X-HM://$encoded$setupId"
    }

    /** `sh` TXT record: first 4 bytes of SHA-512(setupId + accessoryId), base64. */
    fun setupHash(setupId: String, accessoryId: String): String {
        val digest = MessageDigest.getInstance("SHA-512").digest((setupId + accessoryId).toByteArray())
        return Base64.getEncoder().encodeToString(digest.copyOf(4))
    }
}
