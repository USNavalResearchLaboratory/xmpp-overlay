package mil.navy.nrl.xop.client

import kotlinx.coroutines.*
import mil.navy.nrl.xop.util.addressing.getBindAddress
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.chat.ChatManager
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.jxmpp.jid.impl.JidCreate
import java.security.SecureRandom
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.system.exitProcess

private val sslContext = SSLContext.getInstance("TLS")
private val XOPDOMAIN = "xo.nrl.navy.mil"

fun main(args: Array<String>) = runBlocking {
    if (args.size != 3) {
        println("Usage SmackSender <bindInterface> <port> <send|receive")
        exitProcess(-1)
    }

    val bindAddress = getBindAddress(args[0])
    val port = args[1].toInt()
    val send = args[2].toLowerCase() == "send"
    println("using $bindAddress and $port as ${if (send) "SENDER" else "RECEIVER"}")

    sslContext.init(null, arrayOf<TrustManager>(object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate>? {
            return null
        }

        override fun checkClientTrusted(
            certs: Array<java.security.cert.X509Certificate>,
            authType: String
        ) {
        }

        override fun checkServerTrusted(
            certs: Array<java.security.cert.X509Certificate>,
            authType: String
        ) {
        }
    }), SecureRandom())

    val config = XMPPTCPConnectionConfiguration.builder()
        .setXmppDomain(XOPDOMAIN)
        .setUsernameAndPassword("user", "anypassword")
        .setHostAddress(bindAddress)
//            .setHost("172.16.0.1")
        .setResource("resource")
        .setPort(5222)
        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .setCustomSSLContext(sslContext)
        .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
        .build()

    val user2 = "user2"
    val jidUser2 = "$user2@${XOPDOMAIN}"
    val config2 = XMPPTCPConnectionConfiguration.builder()
        .setXmppDomain(XOPDOMAIN)
        .setUsernameAndPassword(user2, "anypassword")
        .setHost("172.16.0.1")
        .setResource("resource")
        .setPort(5222)
        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .setCustomSSLContext(sslContext)
        .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
        .build()

    var recvmsg = ""
    val chatmsg = "hello"

    val con1scope = launch {
        val connection = XMPPTCPConnection(config)
        var connected = false
        while (!connected) {
            try {
                println("attempting to connect conn1")
                connection.connect()
                connected = true
                println("Connected conn1 ")
            } catch (e: SmackException.ConnectionException) {
                println("not connected waiting 1s")
                delay(1000L)
            }
        }
        connection.login()
        val chatManager = ChatManager.getInstanceFor(connection)

        chatManager.addChatListener { chat, createdLocally ->
            println("user1 createdLocally $createdLocally, chat: {$chat} ")
        }

//            chatManager.addChatListener { from, message, chat ->
//                println("user1 incoming msg $from $message $chat")
//                recvmsg = message.body
//            }
        val toJID = JidCreate.entityBareFrom(jidUser2)
        val chat = chatManager.createChat(toJID)
        println("sending $chatmsg to $toJID")
        chat.sendMessage(chatmsg)
        println("sent $chatmsg to $toJID")

        println("disconnecting")
        connection.disconnect()
    }
}
