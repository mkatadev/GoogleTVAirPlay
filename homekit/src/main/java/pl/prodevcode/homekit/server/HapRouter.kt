package pl.prodevcode.homekit.server

import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet
import org.json.JSONArray
import org.json.JSONObject
import pl.prodevcode.homekit.accessory.Accessory
import pl.prodevcode.homekit.accessory.Characteristic
import pl.prodevcode.homekit.accessory.HapStatus
import pl.prodevcode.homekit.pairing.PairSetup
import pl.prodevcode.homekit.pairing.PairVerify
import pl.prodevcode.homekit.pairing.PairingStore
import pl.prodevcode.homekit.pairing.PairingsEndpoint

/**
 * Routes HAP requests to pairing, the accessory database and the characteristics API, and
 * pushes characteristic change events to subscribed controllers.
 */
class HapRouter(
    private val store: PairingStore,
    private val accessory: Accessory,
    private val setupCode: () -> String,
    private val listener: Listener,
) : HapRequestHandler {

    interface Listener {
        fun onPaired()
        fun onUnpaired()
        fun onIdentify()
    }

    private val pairings = PairingsEndpoint(store)
    private val verified = CopyOnWriteArraySet<HapConnection>()

    init {
        accessory.services.flatMap { it.characteristics }.forEach { c -> c.onChanged = { broadcast(it) } }
    }

    override fun handle(connection: HapConnection, request: HttpRequest): HttpResponse {
        Log.d(TAG, "${connection.remote}: ${request.method} ${request.path}")
        return when (request.path) {
            "/pair-setup" -> pairSetup(connection, request)
            "/pair-verify" -> pairVerify(connection, request)
            "/identify" -> identify()
            else -> {
                val session = connection.session ?: return HttpResponse.unauthorized()
                when (request.path) {
                    "/pairings" -> {
                        val result = pairings.handle(request.body, session.controller)
                        result.removed?.let { removed ->
                            verified.filter { it.session?.controller?.identifier == removed.identifier && it !== connection }.forEach { it.close() }
                            if (!store.isPaired) {
                                listener.onUnpaired()
                                verified.filter { it !== connection }.forEach { it.close() }
                            }
                        }
                        HttpResponse.tlv(result.response)
                    }
                    "/accessories" -> HttpResponse.json(200, accessory.toJson().toString())
                    "/characteristics" -> when (request.method) {
                        "GET" -> readCharacteristics(request)
                        "PUT" -> writeCharacteristics(connection, request)
                        else -> HttpResponse.notFound()
                    }
                    "/prepare" -> HttpResponse.json(200, """{"status":0}""")
                    else -> HttpResponse.notFound()
                }
            }
        }
    }

    override fun onClosed(connection: HapConnection) { verified -= connection }

    /** Drops every controller connection, e.g. when the accessory stops or pairings are reset. */
    fun closeAll() {
        verified.forEach { it.close() }
        verified.clear()
    }

    private fun pairSetup(connection: HapConnection, request: HttpRequest): HttpResponse {
        val setup = connection.pairSetup ?: PairSetup(store, setupCode) { listener.onPaired() }.also { connection.pairSetup = it }
        return HttpResponse.tlv(setup.handle(request.body))
    }

    private fun pairVerify(connection: HapConnection, request: HttpRequest): HttpResponse {
        val verify = connection.pairVerify ?: PairVerify(store).also { connection.pairVerify = it }
        val response = verify.handle(request.body)
        verify.established?.let { keys ->
            connection.activateSession(keys)
            connection.pairVerify = null
            verified += connection
        }
        return HttpResponse.tlv(response)
    }

    private fun identify(): HttpResponse {
        if (store.isPaired) return HttpResponse.json(400, """{"status":-70401}""")
        listener.onIdentify()
        return HttpResponse.noContent()
    }

    private fun readCharacteristics(request: HttpRequest): HttpResponse {
        val ids = request.query["id"]?.split(',')?.mapNotNull { pair ->
            pair.split('.').takeIf { it.size == 2 }?.let { it[0].toIntOrNull() to it[1].toIntOrNull() }
        } ?: return HttpResponse.badRequest()
        val withMeta = request.query["meta"] == "1"
        val withPerms = request.query["perms"] == "1"
        val withType = request.query["type"] == "1"
        var failed = false
        val items = JSONArray()
        ids.forEach { (aid, iid) ->
            val item = JSONObject().put("aid", aid).put("iid", iid)
            val c = if (aid == accessory.aid && iid != null) accessory.characteristic(iid) else null
            when {
                c == null -> { item.put("status", HapStatus.RESOURCE_MISSING); failed = true }
                !c.readable -> { item.put("status", HapStatus.WRITE_ONLY); failed = true }
                else -> {
                    item.put("value", c.jsonValue())
                    if (withType) item.put("type", c.type)
                    if (withPerms) item.put("perms", JSONArray(c.perms.map { it.hap }))
                    if (withMeta) item.put("format", c.format.hap)
                }
            }
            items.put(item)
        }
        if (failed) items.forEach { if (!it.has("status")) it.put("status", HapStatus.SUCCESS) }
        return HttpResponse.json(if (failed) 207 else 200, JSONObject().put("characteristics", items).toString())
    }

    private fun writeCharacteristics(connection: HapConnection, request: HttpRequest): HttpResponse {
        val writes = runCatching { JSONObject(String(request.body)).getJSONArray("characteristics") }.getOrNull()
            ?: return HttpResponse.badRequest()
        var failed = false
        val results = JSONArray()
        for (i in 0 until writes.length()) {
            val w = writes.getJSONObject(i)
            val aid = w.optInt("aid", -1)
            val iid = w.optInt("iid", -1)
            val c = if (aid == accessory.aid) accessory.characteristic(iid) else null
            var status = HapStatus.SUCCESS
            if (c == null) {
                status = HapStatus.RESOURCE_MISSING
            } else {
                if (w.has("value")) status = c.write(w.get("value"))
                if (status == HapStatus.SUCCESS && w.has("ev")) {
                    status = if (!c.notifies) HapStatus.NOTIFICATION_UNSUPPORTED else {
                        if (w.getBoolean("ev")) connection.subscriptions += c.iid else connection.subscriptions -= c.iid
                        HapStatus.SUCCESS
                    }
                }
                if (status == HapStatus.SUCCESS && w.has("value") && c.readable) broadcast(c, except = connection)
            }
            if (status != HapStatus.SUCCESS) failed = true
            results.put(JSONObject().put("aid", aid).put("iid", iid).put("status", status))
        }
        return if (failed) HttpResponse.json(207, JSONObject().put("characteristics", results).toString())
        else HttpResponse.noContent()
    }

    private fun broadcast(c: Characteristic, except: HapConnection? = null) {
        val event = JSONObject().put(
            "characteristics",
            JSONArray().put(JSONObject().put("aid", c.aid).put("iid", c.iid).put("value", c.jsonValue())),
        ).toString()
        verified.filter { it !== except && c.iid in it.subscriptions }.forEach { it.sendEvent(event) }
    }

    private inline fun JSONArray.forEach(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) block(getJSONObject(i))
    }

    private companion object { const val TAG = "HapRouter" }
}
