package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.util.logger.LogUtils
import org.json.JSONObject
import org.xmpp.packet.JID

data class NORMNode(
    val jidMap: MutableMap<JID, String>,
    val nodeId: Long,
    var seq: Long,
    val mucOccupants: MutableMap<JID, MutableSet<String>>, // full clientJID to list of mucOccupantJIDs
    val mucRooms: MutableSet<String>, // set of JID rooms
    var connected: Boolean = true
)

val logger = LogUtils.getLogger(NORMNode::class.java.name)!!

/**
 * returns a ClientNormNode object from a [jsonStr].
 */
fun fromJSONStr(jsonStr: String): NORMNode {
    val jsonObject = JSONObject(jsonStr)
    logger.finest("jsonObject [[$jsonObject]]")
    logger.finest("jsonStr    [[$jsonStr]]")
    fun extractMap(key1: String): MutableMap<JID, String> {
        val jsonObj = jsonObject.getJSONObject(key1)
        val ret: Map<JID, String> = jsonObj.toMap().map { (k, v) ->
            val newKey = JID(k)
            newKey to v as String
        }.toMap()
        return ret.toMutableMap()
    }

    fun extractMapJID(key1: String): MutableMap<JID, MutableSet<String>> {
        val jsonObj = jsonObject.getJSONObject(key1)
        val ret: Map<JID, MutableSet<String>> = jsonObj.toMap().map { (k, v) ->
            val newKey = JID(k)
            val arr = v as List<*>
            val mucOccs = arr.map { e -> e as String }.toMutableSet()
            newKey to mucOccs
        }.toMap()
        return ret.toMutableMap()
    }

    fun extractSet(key: String): MutableSet<String> {
        val jsonArray = jsonObject.getJSONArray(key)
        val ret: Set<String> = jsonArray.map { e -> e as String }.toSet()
        return ret.toMutableSet()
    }

    val jidMap: MutableMap<JID, String> = extractMap("jidMap")
    val mucOccupants: MutableMap<JID, MutableSet<String>> = extractMapJID("mucOccupants")

    val mucRooms: MutableSet<String> = extractSet("mucRooms")

    return NORMNode(
        jidMap, jsonObject.optLong("nodeId", 1), jsonObject.optLong("seq", 1),
        mucOccupants, mucRooms
    )
}

fun toJSON(normNode: NORMNode): JSONObject {
    return JSONObject(normNode)
}