package pl.prodevcode.homekit.accessory

import org.json.JSONArray
import org.json.JSONObject

class Service(val type: String, val characteristics: List<Characteristic>, val primary: Boolean = false) {
    var iid = 0
        internal set
    internal val linked = ArrayList<Service>()

    fun link(other: Service) = apply { linked += other }

    fun characteristic(type: String): Characteristic =
        characteristics.first { it.type == type }

    fun toJson(withMeta: Boolean): JSONObject = JSONObject().apply {
        put("iid", iid)
        put("type", type)
        if (primary) put("primary", true)
        if (linked.isNotEmpty()) put("linked", JSONArray(linked.map { it.iid }))
        put("characteristics", JSONArray(characteristics.map { it.toJson(withMeta) }))
    }
}

/** A single accessory (aid 1) with sequential instance ids assigned on construction. */
class Accessory(val services: List<Service>) {
    val aid = 1

    init {
        var next = 1
        services.forEach { s ->
            s.iid = next++
            s.characteristics.forEach { c -> c.iid = next++; c.aid = aid }
        }
    }

    fun characteristic(iid: Int): Characteristic? =
        services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.iid == iid }

    fun toJson(): JSONObject = JSONObject().put(
        "accessories",
        JSONArray().put(JSONObject().put("aid", aid).put("services", JSONArray(services.map { it.toJson(withMeta = true) }))),
    )
}
