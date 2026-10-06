package pl.prodevcode.homekit.pairing

import java.io.ByteArrayOutputStream
import pl.prodevcode.homekit.crypto.Tlv8
import pl.prodevcode.homekit.crypto.TlvError
import pl.prodevcode.homekit.crypto.TlvMethod
import pl.prodevcode.homekit.crypto.TlvType

/** `/pairings` (HAP 5.10–5.12): admins add, remove and list controllers over a verified session. */
class PairingsEndpoint(private val store: PairingStore) {

    class Result(val response: ByteArray, val removed: Pairing? = null)

    fun handle(body: ByteArray, caller: Pairing): Result {
        val tlv = runCatching { Tlv8.decode(body) }.getOrNull() ?: return Result(Tlv8.error(2, TlvError.UNKNOWN))
        if (tlv.byte(TlvType.STATE) != 1) return Result(Tlv8.error(2, TlvError.UNKNOWN))
        if (!caller.admin) return Result(Tlv8.error(2, TlvError.AUTHENTICATION))
        return when (tlv.byte(TlvType.METHOD)) {
            TlvMethod.ADD_PAIRING -> add(tlv)
            TlvMethod.REMOVE_PAIRING -> remove(tlv)
            TlvMethod.LIST_PAIRINGS -> list()
            else -> Result(Tlv8.error(2, TlvError.UNKNOWN))
        }
    }

    private fun add(tlv: Tlv8): Result {
        val id = tlv[TlvType.IDENTIFIER]?.let { String(it) } ?: return Result(Tlv8.error(2, TlvError.UNKNOWN))
        val ltpk = tlv[TlvType.PUBLIC_KEY] ?: return Result(Tlv8.error(2, TlvError.UNKNOWN))
        val admin = tlv.byte(TlvType.PERMISSIONS) == 1
        val existing = store.find(id)
        if (existing != null && !existing.publicKey.contentEquals(ltpk)) return Result(Tlv8.error(2, TlvError.UNKNOWN))
        store.add(Pairing(id, ltpk, admin))
        return Result(Tlv8().put(TlvType.STATE, 2).encode())
    }

    private fun remove(tlv: Tlv8): Result {
        val id = tlv[TlvType.IDENTIFIER]?.let { String(it) } ?: return Result(Tlv8.error(2, TlvError.UNKNOWN))
        val removed = store.find(id)
        if (removed != null) {
            store.remove(id)
            // an accessory without an admin can never be managed again: drop everyone and allow a fresh setup
            if (store.pairings.value.none { it.admin }) store.removeAll()
        }
        return Result(Tlv8().put(TlvType.STATE, 2).encode(), removed)
    }

    private fun list(): Result {
        val out = ByteArrayOutputStream()
        out.write(Tlv8().put(TlvType.STATE, 2).encode())
        store.pairings.value.forEachIndexed { i, p ->
            if (i > 0) out.write(Tlv8().put(TlvType.SEPARATOR, ByteArray(0)).encode())
            out.write(
                Tlv8().put(TlvType.IDENTIFIER, p.identifier).put(TlvType.PUBLIC_KEY, p.publicKey)
                    .put(TlvType.PERMISSIONS, if (p.admin) 1 else 0).encode(),
            )
        }
        return Result(out.toByteArray())
    }
}
