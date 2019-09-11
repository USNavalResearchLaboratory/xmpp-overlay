package mil.navy.nrl.xop.client

import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.net.SDListener
import edu.drexel.xop.net.SDManager
import edu.drexel.xop.packet.LocalPacketProcessor
import edu.drexel.xop.room.Room
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jivesoftware.smack.ConnectionConfiguration
import org.jivesoftware.smack.StanzaListener
import org.jivesoftware.smack.chat2.ChatManager
import org.jivesoftware.smack.filter.StanzaFilter
import org.jivesoftware.smack.packet.IQ
import org.jivesoftware.smack.roster.Roster
import org.jivesoftware.smack.tcp.XMPPTCPConnection
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration
import org.junit.jupiter.api.*
import org.jxmpp.jid.impl.JidCreate
import org.xmpp.packet.JID
import org.xmpp.packet.Presence
import java.net.InetAddress
import java.security.SecureRandom
import java.util.logging.Logger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
//@Execution(ExecutionMode.SAME_THREAD)
class ClientConnectionTest {

    companion object {
        val logger: Logger = LogUtils.getLogger(ClientConnectionTest::javaClass.name)
    }

    private val sslContext = SSLContext.getInstance("TLS")

    init {
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
    }


    private var listenThread: Job? = null

    @BeforeEach
    fun setUp() {
        running.set(true)
        val loopbackAddr = InetAddress.getLoopbackAddress()
//        val clientManager = XOProxy.getInstance().clientManager
        val clientManager = ClientManager()

        val sdManager = object : SDManager {
            override fun close() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun start() {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun addGateway(address: InetAddress?, domain: JID?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun removeGateway(domain: JID?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun advertiseClient(presence: Presence?) {
                println("advertising client: $presence")
            }

            override fun removeClient(presence: Presence?) {
                println("removed client: $presence")
            }

            override fun updateClientStatus(presence: Presence?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun advertiseMucOccupant(presence: Presence?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun removeMucOccupant(presence: Presence?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun updateMucOccupantStatus(presence: Presence?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun addSDListener(listener: SDListener?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun advertiseMucRoom(room: Room?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun removeMucRoom(room: Room?) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

        }
        val localPacketProcessor = LocalPacketProcessor(clientManager, sdManager)
        listenThread = GlobalScope.launch {
            listenForClients(loopbackAddr, 5222, clientManager, localPacketProcessor, "localhost")
        }
        logger.info("started clt on $loopbackAddr")
    }

    @AfterEach
    fun tearDown() {
        logger.info("tearDown")
        running.set(false)
        listenThread?.cancel()
    }

    private val user = "user"
    private val user2 = "user2"

    @Test
    fun testSendMessage() {
        logger.info("starting testSendMessage")
        // logs in and sends a message to self
//        val connection = XMPPTCPConnection(config)
        val config2 = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(XOP.DOMAIN)
            .setUsernameAndPassword(user2, "anypassword")
            .setHost("localhost")
            .setResource("resource")
            .setPort(5222)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
            .setCustomSSLContext(sslContext)
            .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
            .build()
        val conn2 = XMPPTCPConnection(config2)
        conn2.connect()
        conn2.login()

        val chatManager2 = ChatManager.getInstanceFor(conn2)

//        var recvmsg = ""
//        val procIncoming = { chat: Chat, createdLocally: Boolean ->
//            chat { _, message ->
//                println("message $message ${message.body}")
//                if (!createdLocally) {
//                    recvmsg = message.body
//                }
//            }
//            println("user1 createdLocally $createdLocally")
//        }
        chatManager2.addIncomingListener { from, msg, chat ->
            println("from: $from")
            println("msg: $msg")
            println("chat: $chat")
            logger.info("from: $from")
            logger.info("msg: $msg")
            logger.info("chat: $chat")

        }

        // val chatmsg = "hello"
//        val toJID2 = JidCreate.entityBareFrom(jidUser2)
//        val chat2 = chatManager2.createChat(toJID2)
//        println("sending $chatmsg to $toJID2")
//        chat2.sendMessage(chatmsg)
//        println("sent $chatmsg to $toJID2")
//        val d = launch {
//            connection.replyTimeout = 3000L
//            connection.connect()
//            connection.login()
//
//            val chatManager = ChatManager.getInstanceFor(connection)
//            //        chatManager.addChatListener { chat, createdLocally ->
//            //            chat.addMessageListener { chat, message -> println("message $message ${message.body}") }
//            //            println("user2 createdLocally $createdLocally, chat: {${chat}} ")
//            //        }
//            val toJID = JidCreate.entityBareFrom(jidUser2)
//            val chat = chatManager.createChat(toJID)
//            println("sending $chatmsg to $toJID")
//            chat.sendMessage(chatmsg)
//            println("sent $chatmsg to $toJID")
//
//            Thread.sleep(1000L)
//            Assertions.assertTrue(recvmsg == chatmsg, "{$recvmsg} != {$chatmsg}")
//
//            //        Thread.sleep(3000L)
//            println("disconnecting")
//            connection.disconnect()
//        }
//        d.join()
        conn2.disconnect()
    }

    @Test
    fun testSendIQRoster()  {
        logger.info("starting testSendIQRoster")
        // logs in and sends a message to self
        //        val connection = XMPPTCPConnection(config)
        val config = XMPPTCPConnectionConfiguration.builder()
            .setXmppDomain(XOP.DOMAIN)
            .setUsernameAndPassword(user, "anypassword")
            .setHost("localhost")
            .setResource("resource")
            .setPort(5222)
            .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
            .setCustomSSLContext(sslContext)
            .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
            .build()
        val conn2 = XMPPTCPConnection(config)
        conn2.connect()
        conn2.login()

        val callback = StanzaListener { packet ->
            packet!! is IQ
            logger.info("packet $packet")
        }

        val stanzaFilter = StanzaFilter { stanza -> stanza!! is IQ }
//        conn2.addAsyncStanzaListener(callback, stanzaFilter)
        conn2.addOneTimeSyncCallback(callback, stanzaFilter)

        val roster = Roster.getInstanceFor(conn2)
        roster.subscriptionMode = Roster.SubscriptionMode.accept_all

        val user1Jid = JidCreate.bareFrom("$user2@${XOP.DOMAIN}")

        roster.createEntry(user1Jid, "user name", arrayOf("group"))
//        delay(500L)
        conn2.disconnect()

    }

//    @Test
//    fun testCleanupAfterClosedConnection()  {
//        // user is able to connect, send presence, and XOP cleans up users if connection is unexpectedly closed
//        val config = XMPPTCPConnectionConfiguration.builder()
//            .setXmppDomain(XOP.DOMAIN)
//            .setUsernameAndPassword("user3", "anypassword")
//            .setHost("localhost")
//            .setResource("resource")
//            .setPort(5222)
//            .setSecurityMode(ConnectionConfiguration.SecurityMode.required)
//            .setCustomSSLContext(sslContext)
//            .setHostnameVerifier { _, _ -> true } // needed to disable hostname checking of the certificate
//            .build()
//        val connection = XMPPTCPConnection(config)
//        connection.replyTimeout = 3000L
//        connection.connect()
//        connection.login()
//
//        connection.disconnect()
//        // delay(500L)
//        connection.connect()
//        connection.login()
//        connection.disconnect()
//    }
}