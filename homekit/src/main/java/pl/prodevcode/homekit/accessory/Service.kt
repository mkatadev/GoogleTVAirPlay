package pl.prodevcode.homekit.accessory

import org.json.JSONArray
import org.json.JSONObject

/** [iid] 0 = assigned sequentially by [Accessory]; a fixed value keeps the id stable across database rebuilds. */
class Service(val type: String, val characteristics: List<Characteristic>, val primary: Boolean = false, iid: Int = 0) {
    var iid = iid
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

/** A single accessory (aid 1); services and characteristics without a fixed iid get sequential ones on construction. */
class Accessory(val services: List<Service>) {
    val aid = 1

    init {
        var next = 1
        services.forEach { s ->
            if (s.iid == 0) s.iid = next++
            s.characteristics.forEach { c ->
                if (c.iid == 0) c.iid = next++
                c.aid = aid
            }
        }
        val ids = services.flatMap { s -> listOf(s.iid) + s.characteristics.map { it.iid } }
        require(ids.size == ids.toSet().size) { "duplicate instance ids: ${ids.groupBy { it }.filterValues { it.size > 1 }.keys}" }
    }

    fun characteristic(iid: Int): Characteristic? =
        services.asSequence().flatMap { it.characteristics.asSequence() }.firstOrNull { it.iid == iid }

    fun toJson(): JSONObject = JSONObject().put(
        "accessories",
        JSONArray().put(JSONObject().put("aid", aid).put("services", JSONArray(services.map { it.toJson(withMeta = true) }))),
    )
}
