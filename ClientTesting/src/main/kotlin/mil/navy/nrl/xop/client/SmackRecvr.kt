package mil.navy.nrl.xop.client

import kotlinx.coroutines.*
import mil.navy.nrl.xop.util.addressing.getBindAddress
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.SmackException
import org.jivesoftware.smack.chat2.ChatManager
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

fun main(args: Array<String>) = runBlocking<Unit> {
    if (args.size < 2) {
        println("Usage SmackRecvr <bindInterface> <port>")
        exitProcess(-1)
    }

    val bindAddress = getBindAddress(args[0])
    val port = args[1].toInt()
    println("using $bindAddress and $port")

    sslContext.init(
        null,
        arrayOf<TrustManager>(object : X509TrustManager {
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
        }),
        SecureRandom()
    )

    val user2 = "user2"
    val config2 = XMPPTCPConnectionConfiguration.builder()
        .setXmppDomain(XOPDOMAIN)
        .setUsernameAndPassword(user2, "anypassword")
        .setHost("localhost")
        .setResource("resource")
        .setPort(5222)
        .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
        .setCustomSSLContext(sslContext)
        .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
        .build()

    var recvmsg = ""
    val chatmsg = "hello"

    val con2scope = launch {
        val conn2 = XMPPTCPConnection(config2)
        var connected = false
        while (!connected) {
            try {
                println("attempting to connect conn2")
                conn2.connect()
                connected = true
                println("Connected conn2")
            } catch (e: SmackException.ConnectionException) {
                println("not connected waiting 1s")
                Thread.sleep(1000L)
                println("done waiting, try again")
            }
        }
        println("logging in")
        conn2.login()

//        val chatManager2 = ChatManager.getInstanceFor(conn2)
//
//        chatManager2.addIncomingListener { from, message, chat ->
//            println("user2 New message from $from message{$message} $chat")
////            recvmsg = message.body
//        }
        delay(10000L)

        conn2.disconnect()
    }

    /*
      } else {
        val con2scope = launch{
            val conn2 = XMPPTCPConnection(config2)
            var connected = false
            while(!connected) {
                try {
                    println("attempting to connect conn2")
                    conn2.connect()
                    connected = true
                    println("Connected conn2")
                } catch (e: SmackException.ConnectionException){
                    println("not connected waiting 1s")
                    Thread.sleep(1000L)
                    println("done waiting, try again")
                }
            }
            println("logging in as ${config2.username}")
            conn2.login()

            val chatManager2 = ChatManager.getInstanceFor(conn2)

            chatManager2.addChatListener { chat, createdLocally ->
                chat.addMessageListener { chat, message -> println("message $message ${message.body}") }
                println("user2 createdLocally $createdLocally, chat: {${chat}} ")
            }
            delay(10000L)

            println("disconnecting")
            conn2.disconnect()
        }
    }
     */


}
