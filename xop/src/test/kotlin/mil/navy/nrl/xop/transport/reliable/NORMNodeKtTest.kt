package mil.navy.nrl.xop.transport.reliable

import org.json.JSONObject
import org.junit.jupiter.api.Test
import org.xmpp.packet.JID

internal class NORMNodeKtTest {

    @Test
    fun fromJSON() {
        // val dataStr = "{\"jidMap\":{\"duc@proxy/rsrc\":\"deadbeef\"},\"connected\":true," +
        //         "\"mucOccupants\":{\"duc@proxy/rsrc\":[\"room@conference.proxy/duc\",\"room2@conference.proxy/duc\"]}," +
        //         "\"nodeId\":100," +
        //         "\"mucRooms\":[\"room@conference.proxy\",\"room2@conference.proxy\"]}"

        var jid = JID("duc@proxy")
        val emptyClient = NORMNode(
            mutableMapOf(jid to ""), 100, 0,
            mutableMapOf(),
            mutableSetOf()
        )

        val jsonObject = JSONObject(emptyClient)
        println("jsonObject    $jsonObject")

        println("fromJSONStr   ${fromJSONStr(jsonObject.toString())}")
        println("client        $emptyClient")
        //    println("jsonObject ${JSON.unquoted.stringify(client)}")

        jid = JID("duc@proxy/rsrc")
        emptyClient.jidMap[jid] = "deadbeef"
        println("client $emptyClient")
        println("json:  ${JSONObject(emptyClient)}")
        println("marsh: ${fromJSONStr(JSONObject(emptyClient).toString())}")

        val client = NORMNode(
            mutableMapOf(jid to "deadbeef"), 100, 0,
            mutableMapOf(
                JID("duc@proxy/rsrc") to mutableSetOf(
                    "room@conference.proxy/duc",
                    "room2@conference.proxy/duc"
                )
            ),
            mutableSetOf("room@conference.proxy")
        )

        val clientJSON = JSONObject(client)
        println("client       $client")
        println("jsonObjStr   $clientJSON")
        println("fromJSONStr  ${fromJSONStr(clientJSON.toString())}")
    }

    @Test
    fun toJSON() {
        val jid = JID("duc@proxy")
        val emptyClient = NORMNode(
            mutableMapOf(jid to "deadbeef"), 100, 0,
            mutableMapOf(),
            mutableSetOf()
        )

        emptyClient.seq += 1

        emptyClient.mucOccupants[jid] = mutableSetOf("room@conference.proxy/duc", "room2@conference.proxy/duc")
        emptyClient.mucRooms.add("room@conference.proxy")
        emptyClient.mucRooms.add("room2@conference.proxy")

        val jsonObj = JSONObject(emptyClient)
        println(jsonObj.toString())
        println(fromJSONStr(jsonObj.toString()))

    }
}