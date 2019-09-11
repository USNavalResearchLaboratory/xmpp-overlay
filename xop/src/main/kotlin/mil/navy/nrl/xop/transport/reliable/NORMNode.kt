package mil.navy.nrl.xop.transport.reliable

import edu.drexel.xop.util.logger.LogUtils
import mil.navy.nrl.norm.NormSession
import org.json.JSONObject
import org.xmpp.packet.JID

data class NORMNode(
    val jidMap: MutableMap<JID, String>,  // Map of available clients local to this client
    val nodeId: Long,
    var seq: Long,
    val mucOccupants: MutableMap<JID, MutableSet<String>>, // full clientJID to list of mucOccupantJIDs
    val mucRooms: MutableSet<String>, // set of JID rooms
    val sendingSession: NormSession? = null,
    var connected: Boolean = true
)

data class XMPPEntity(
    val jid: JID,
    val nodeId: Long,
    val hash: String, // hash of status, show, element
    val mucOccupants: MutableSet<String>
)

val logger = LogUtils.getLogger(NORMNode::class.java.name)!!

/**
 * Returns a ClientNormNode object from a [jsonStr].
 */
fun fromJSONStr(jsonStr: String, normSession: NormSession? = null): NORMNode {
    val jsonObject = JSONObject(jsonStr)
    logger.finest("jsonObject [[$jsonObject]]")
    logger.finest("jsonStr    [[$jsonStr]]")
    fun extractMap(jidMapKey: String): MutableMap<JID, String> {
        val jidMapJSONObject = jsonObject.getJSONObject(jidMapKey)
        val ret: Map<JID, String> = jidMapJSONObject.toMap().map { (k, v) ->
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
        mucOccupants, mucRooms, normSession
    )
}

/**
 * Returns a JSONObject representation of the [normNode]. NOTE: removes the "sendingSession" value
 */
fun toJSON(normNode: NORMNode): JSONObject {
    val obj = JSONObject(normNode)
    obj.remove("sendingSession")
    return obj
}