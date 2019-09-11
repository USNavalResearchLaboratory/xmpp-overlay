package mil.navy.nrl.xop.client

import edu.drexel.xop.client.AuthenticationProvider
import edu.drexel.xop.client.XOPConnection
import edu.drexel.xop.core.ClientManager
import edu.drexel.xop.core.LocalXMPPClient
import edu.drexel.xop.core.XOProxy
import edu.drexel.xop.net.SDListener
import edu.drexel.xop.net.SDManager
import edu.drexel.xop.packet.LocalPacketProcessor
import edu.drexel.xop.room.Room
import edu.drexel.xop.util.Base64
import edu.drexel.xop.util.CONSTANTS
import edu.drexel.xop.util.Utils
import edu.drexel.xop.util.XOP
import edu.drexel.xop.util.logger.LogUtils
import kotlinx.coroutines.runBlocking
import mil.navy.nrl.xop.util.addressing.getBindAddress
import org.dom4j.Element
import org.dom4j.Namespace
import org.dom4j.tree.BaseElement
import org.xmpp.packet.*
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.*
import java.security.cert.CertificateException
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import javax.net.ssl.*
import javax.xml.stream.XMLInputFactory
import javax.xml.stream.XMLStreamConstants
import javax.xml.stream.XMLStreamReader
import kotlin.concurrent.thread
import kotlin.system.exitProcess


private val logger = LogUtils.getLogger(ClientConnection::class.java.name)

val running: AtomicBoolean = AtomicBoolean(true)

fun listenForClients(
    bindAddress: InetAddress, port: Int,
    clientManager: ClientManager,
    localPacketProcessor: LocalPacketProcessor,
    domain: String
): Thread {

    val serverSocket = ServerSocket()
    serverSocket.reuseAddress = true
    serverSocket.bind(InetSocketAddress(bindAddress, port))
    logger.info("Listening for clients on ${serverSocket.localSocketAddress} on domain: $domain")

    val ret = thread {
        while (running.get()) {
            val socket = serverSocket.accept()
            logger.info("Accepted connection from ${socket.inetAddress}:${socket.port}")
            val cc = ClientConnection(socket, clientManager, localPacketProcessor, domain)
            Thread(cc).start()
        }
    }
    logger.info("coroutine for clientConnections exiting, returning Job with $ret")
    return ret
}

fun main(args: Array<String>) = runBlocking {
    if (args.size < 3) {
        println("Usage ClientConnectionKt <bindInterface> <port> <domain>")
        exitProcess(-1)
    }

    val bindAddress = getBindAddress(args[0])
    val port = args[1].toInt()
    val domain = args[2]

    //    val processPacket = fun(jid:JID, packet:Packet, isLocal: Boolean) {
    //                        println("TESTING local $isLocal From $jid, packet: {{$packet}}")
    //                    }
    //    val clientCoroutine = listenForClients(bindAddress, port, processPacket) // LocalPacketProcessor.getInstance()::processPacket)

    val sdManager = object : SDManager {
        override fun start() {
        }

        override fun close() {
        }

        override fun addGateway(address: InetAddress?, domain: JID?) {
        }

        override fun removeGateway(domain: JID?) {
        }

        override fun advertiseClient(presence: Presence?) {
            println("===========> Advertise client $presence")
        }

        override fun removeClient(presence: Presence?) {
            println("===========> REMOVE client $presence")
        }

        override fun updateClientStatus(presence: Presence?) {
            println("===========> Update Status $presence")

        }

        override fun advertiseMucOccupant(presence: Presence?) {
        }

        override fun removeMucOccupant(presence: Presence?) {
        }

        override fun updateMucOccupantStatus(presence: Presence?) {
        }

        override fun addSDListener(listener: SDListener?) {
        }

        override fun advertiseMucRoom(room: Room?) {
        }

        override fun removeMucRoom(room: Room?) {
        }

    }

    val clientManager = ClientManager()

    val pm = LocalPacketProcessor(clientManager, sdManager)
    val clientCoroutine = listenForClients(bindAddress, port, clientManager, pm, domain)
    println("listening for clients")
    // clientCoroutine.join()
}

/**
 * Represents connection to an XMPP client
 */
class ClientConnection(
    private var socket: Socket,
    private val clientManager: ClientManager,
    private val localPacketProcessor: LocalPacketProcessor,
    private val domain: String = XOP.DOMAIN
) : XOPConnection {
    data class Feature(
        val localName: String,
        val uri: String,
        val elements: List<Element>
    )

    private val startTLSNS = "urn:ietf:params:xml:ns:xmpp-tls"

    private var inputStream = socket.getInputStream()
    private var outputStream = socket.getOutputStream()
    private var xmlStreamReader: XMLStreamReader

    private var jid: JID? = null
    private var socketFactory: SSLSocketFactory? = null

    private companion object {
        private val logger = LogUtils.getLogger(ClientConnection::class.java.name)
    }

    private enum class ClientConnectionState {
        OPENSTREAM, FEATURES, NEGOTIATE_TLS, AUTHENTICATE, AUTH_SUCCESS, BIND, STANZA_EXCHANGE, AUTH_FAIL, CLOSED
    }

    private var streamId: String
    private var currentState = ClientConnectionState.OPENSTREAM

    private val sendQueue: BlockingQueue<String> = LinkedBlockingQueue<String>()

    private val running: AtomicBoolean = AtomicBoolean(true)
    private val xmlif = XMLInputFactory.newInstance()

    private val authenticationProvider = AuthenticationProvider(clientManager)
    private var authenticated = false

    override fun writeRaw(bytes: ByteArray?) {
        logger.finest("writing ${bytes?.size} bytes to $jid")
        if (socket.isConnected) {
            outputStream.write(bytes)
            outputStream.flush()
        } else {
            logger.fine("not writing because socket not connected")
        }
        logger.finest("wrote ${bytes?.size} bytes to $jid")
    }

    override fun processCloseStream() {
        logger.fine("handling close stream")
        if (running.get()) {
            val jid = clientManager.getJIDForLocalConnection(this)

            XOProxy.getInstance().handleCloseStream(jid)
        }
        enqueueString(CONSTANTS.AUTH.STREAM_CLOSE)
    }

    override fun getAddress(): InetAddress {
        return socket.inetAddress
    }

    override fun getHostName(): String {
        return socket.inetAddress.hostName
    }

    init {
        xmlStreamReader = xmlif.createXMLStreamReader(inputStream)
        streamId = Utils.generateID(10)
        // Create a thread to send out messages

        // startMessageThread()
    }

    private fun startMessageThread() {
        logger.fine("starting sending thread")
        running.set(true)
        thread(name = "ClientMessageSendingThread") {
            outputStream.use { outputStream ->
                while (running.get()) {
                    val str = sendQueue.take()
                    logger.finer("writing: {{$str}}")
                    outputStream.write(str.toByteArray())
                    outputStream.flush()
                }
            }

            logger.finer("ClientMessageSendingThread Sending thread exiting")
        }
    }

    private fun enqueueString(str: String) {
        logger.finer("Adding string to outputStream queue")
        //        sendQueue.put(str)
        writeRaw(str.toByteArray())
    }

    fun closeConnection() {
        enqueueString(CONSTANTS.AUTH.STREAM_CLOSE)
        // TODO close
        running.set(false)
    }

    override fun run() {
        logger.info("Processing new Client Connection")


        try {

        var eventType: Int
            // get the stax parser
            while (xmlStreamReader.hasNext()) {
                eventType = xmlStreamReader.next()
                logger.finer("processing eventType $eventType")
                when (eventType) {
                    XMLStreamConstants.START_DOCUMENT -> {
                        logger.finer("Start of the document")
                        if (logger.isLoggable(Level.FINER)) {
                            logger.finer("localName ${xmlStreamReader.localName}")
                        }
                    }
                    XMLStreamConstants.START_ELEMENT -> {
                        logger.finer("Start Element again")
                        if (currentState != ClientConnectionState.STANZA_EXCHANGE)
                            processStartElement()
                        else {
                            logger.fine("STANZA_EXCHANGE state, building element")
                            val element = buildElement()
                            val packet: Packet
                            when (element.name) {
                                "iq" -> {
                                    logger.fine("IQ message: ${element.asXML()}")
                                    packet = IQ(element)
                                }
                                "presence" -> {
                                    logger.fine("Presence message: ${element.asXML()}")
                                    packet = Presence(element)
                                }
                                "message" -> {
                                    logger.fine("message message: ${element.asXML()}")
                                    packet = Message(element)
                                }
                                else -> {
                                    logger.warning("should not happen!")
                                    packet = Message()
                                }
                            }
                            packet.from = jid
                            //                        logger.fine("Calling processIncomingPacket with $jid $packet")
                            localPacketProcessor.processPacket(jid!!, packet)
                        }
                    }

                    XMLStreamConstants.END_ELEMENT -> {
                        val localName = xmlStreamReader.localName
                        val uri = xmlStreamReader.namespaceURI

                        logger.fine("END Element $localName $uri")
                        processCloseStream()
                    }

                    XMLStreamConstants.END_DOCUMENT -> {
                        logger.finer("End of the document")
                    }

                    XMLStreamConstants.CHARACTERS -> {
                        val chars = xmlStreamReader.textCharacters
                        logger.fine("received characters: $chars")
                    }
                    else -> {
                        logger.info("UNHANDLED Event Type, $eventType,  in main stream processor")
                    }
                } // end when(eventType)
            } // end while hasNext()
        } finally {
            processCloseStream()

            running.set(false)
            logger.info("closing XMLStreamReader")
            xmlStreamReader.close()
            logger.info("closing socket")
            socket.close()
            logger.info("closed connection")

        }

    }

    private fun processStartElement() {
        val localName = xmlStreamReader.localName
        val namespace = xmlStreamReader.namespaceURI
        val attrCt = xmlStreamReader.attributeCount

        logger.fine("Start Element $localName $namespace numAttr: $attrCt currentState $currentState")
        if (logger.isLoggable(Level.FINER)) {
            for (i in 0..(attrCt - 1)) {
                val name = xmlStreamReader.getAttributeName(i)
                val value = xmlStreamReader.getAttributeValue(i)
                val type = xmlStreamReader.getAttributeType(i)
                logger.finer("attr $name $value $type")
            }
        }

        when (currentState) {
            ClientConnectionState.OPENSTREAM -> {
                var nextState = ClientConnectionState.FEATURES
                var sendStr = CONSTANTS.AUTH.STREAM_FEATURES_TLS_OPTIONAL
                if (XOP.TLS.AUTH) {
                    logger.info("TLS required")
                    sendStr = CONSTANTS.AUTH.STREAM_FEATURES_TLS
                    nextState = ClientConnectionState.NEGOTIATE_TLS
                }
                handleOpenStream(localName, nextState, sendStr)
            }
            ClientConnectionState.FEATURES -> {
                logger.fine("negotiate features state")
                processFeatures()
            }

            ClientConnectionState.NEGOTIATE_TLS -> {
                handleStartTLS(localName, namespace)
            }

            ClientConnectionState.AUTHENTICATE -> {
                val nextState = ClientConnectionState.FEATURES
                val sendStr = CONSTANTS.AUTH.STREAM_FEATURES_PLAIN
                handleOpenStream(localName, nextState, sendStr)
            }

            ClientConnectionState.AUTH_SUCCESS -> {
                val nextState = ClientConnectionState.BIND
                val sendStr = CONSTANTS.AUTH.STREAM_FEATURES
                handleOpenStream(localName, nextState, sendStr)

                val localXMPPClient = LocalXMPPClient(jid, jid.toString(), null, null, this)
                clientManager.addLocalXMPPClient(localXMPPClient)
                logger.info("Added " + jid + " to clientManager: " + clientManager.localClientJIDs)
            }

            ClientConnectionState.BIND -> {
                logger.fine("BIND state, building element")
                val bindIqElement = buildElement()
                val bindIq = IQ(bindIqElement)
                localPacketProcessor.processPacket(jid, bindIq)
                // TODO 2018-12-05 use the localPacketProcessor
                //IqManager().processIQPacket(jid, bindIq)
                currentState = ClientConnectionState.STANZA_EXCHANGE
            }
            else -> {
                logger.info("Unhandled state $currentState")
            }
        }
    }

    private fun handleOpenStream(
        localName: String?,
        nextState: ClientConnectionState,
        featureStr: String
    ) {
        if (localName == "stream") {
            val fromJIDStr = xmlStreamReader.getAttributeValue(null, "from")
            if (fromJIDStr != null && jid == null) {
                jid = JID(fromJIDStr)

                if (jid?.domain != this.domain) {
                    logger.info("from domain is not the same as ${this.domain} closing")
                    closeConnection()
                    return
                }
            }

            // val to = xmlStreamReader.getAttributeValue(null, "to")
            val version = xmlStreamReader.getAttributeValue(null, "version")
            if (version!!.split(".")[0].toInt() >= 1) { // RFC 6120 4.3.2
                logger.info("changing state to $nextState")
                currentState = nextState
                logger.info("New stream jid $jid.")
                var toJID = ""
                if (jid != null){
                    toJID = "to='$jid'"
                }
                logger.info("Sending open stream with id $streamId")
                var sendStr =
                    "<stream:stream from='${this.domain}' ${toJID} id='$streamId' " +
                            "xmlns=\"jabber:client\" xmlns:stream=\"http://etherx.jabber.org/streams\"" +
                            " version=\"1.0\" xml:lang='en'>"

                sendStr += featureStr
                logger.info("Sending open stream with features $sendStr")
                enqueueString(sendStr)
            } else {
                logger.info("Client connection does not support streams version > 1.0")
                closeConnection()
            }
        } else {
            logger.warning("not an open stream!")
        }
    }

    private fun handleStartTLS(localName: String?, namespace: String?) {
        if (localName == "starttls" && namespace == startTLSNS) {
            logger.info("Received STARTTLS jid client, send proceed")
            enqueueString(CONSTANTS.AUTH.STARTTLS_PROCEED)
            currentState = ClientConnectionState.NEGOTIATE_TLS
            negotiateTLS()
        } else {
            logger.info("STARTTLS negotiation failed")
            enqueueString(CONSTANTS.AUTH.STARTTLS_FAIL)
            closeConnection()
        }
    }

    private fun buildElement(): Element {
        val rootElementName = xmlStreamReader.localName
        var eventType: Int = xmlStreamReader.eventType
        var localName = xmlStreamReader.localName
        var uri = xmlStreamReader.namespaceURI
        val returnElement = BaseElement(localName, Namespace(null, uri))
        val addAttributes =
            fun(nestedElement: Element, xmlStreamReader: XMLStreamReader): Element {
                for (i in 0 until xmlStreamReader.attributeCount) nestedElement.addAttribute(
                    xmlStreamReader.getAttributeLocalName(i),
                    xmlStreamReader.getAttributeValue(i)
                )
                return nestedElement
            }
        addAttributes(returnElement, xmlStreamReader)

        var nestedElement: Element? = returnElement
        logger.finer("eventType: $eventType, localName $localName, uri $uri, ")
        elementProcessing@ while (xmlStreamReader.hasNext()) {
            eventType = xmlStreamReader.next()
            when (eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    localName = xmlStreamReader.localName
                    uri = xmlStreamReader.namespaceURI
                    logger.finer("start element type $localName $uri")

                    nestedElement = nestedElement!!.addElement(localName, uri)

                    addAttributes(nestedElement, xmlStreamReader)
                    logger.finer("current element: ${returnElement.asXML()}")
                }

                XMLStreamConstants.CHARACTERS -> {
                    nestedElement!!.addText(xmlStreamReader.text)
                    logger.finer("after characters current element: ${returnElement.asXML()}")
                }

                XMLStreamConstants.END_ELEMENT -> {
                    localName = xmlStreamReader.localName
                    logger.finer("end element localName $localName")

                    if (localName == rootElementName) {
                        logger.finer("Reached end of this element, breaking out")
                        break@elementProcessing
                    } else {
                        logger.finer("end element localName $localName")
                        nestedElement = nestedElement!!.parent
                    }
                }
                else -> {
                    logger.finer("unhandled event type $eventType")
                }
            }
        }

        logger.fine("built element: ${returnElement.asXML()}")
        return returnElement
    }

    private fun processFeatures() {
        var localName: String?
        var namespaceURI: String?
        var features = mutableListOf<Feature>()
        var featureNumber = 0
        var newFeature: Feature

        var eventType: Int = xmlStreamReader.eventType
        var text = ""

        featureProcessing@ do {
            when (eventType) {
                XMLStreamConstants.START_ELEMENT -> {
                    localName = xmlStreamReader.localName
                    namespaceURI = xmlStreamReader.namespaceURI
                    logger.fine("start element $localName $namespaceURI")
                    features = mutableListOf()
                    featureNumber = 0
                    newFeature = Feature(localName, namespaceURI, mutableListOf())
                    features.add(newFeature)
                }

                XMLStreamConstants.CHARACTERS -> {
                    if (xmlStreamReader.isCharacters) {
                        text = xmlStreamReader.text
                        logger.fine("text Characters $text")
                    } else {
                        logger.fine("not text characters")
                    }
                }

                XMLStreamConstants.END_ELEMENT -> {
                    localName = xmlStreamReader.localName
                    namespaceURI = xmlStreamReader.namespaceURI

                    logger.fine("END Element $localName $namespaceURI")
                    if (localName == "features") {
                        logger.fine("end processing features")
                        break@featureProcessing
                    }
                    logger.fine("added feature")
                    featureNumber++
                    logger.fine("features: $features")
                    for (feature in features.toList()) {
                        logger.fine("Processing feature: $feature")

                        when (feature.localName) {
                            "starttls" -> {
                                handleStartTLS(feature.localName, feature.uri)
                                currentState = ClientConnectionState.AUTHENTICATE
                                features.removeAt(--featureNumber)
                                break@featureProcessing
                            }
                            "auth" -> {
                                logger.fine("authenticate")
                                authenticate(text)
                                features.removeAt(--featureNumber)
                                break@featureProcessing
                            }
                        }
                    }
                }
                else -> {
                    logger.info("UNHANDLED Event Type in main stream processor $eventType")
                }
            }
            logger.fine("processed event, has more? ${xmlStreamReader.hasNext()}")
            // pop each feature and handle
            logger.fine("features: $features")
            eventType = xmlStreamReader.next()
        } while (xmlStreamReader.hasNext()) // end while

    }

    private fun authenticate(text: String) {
        val creds = Base64.decode(text).split("\\00".toRegex())
            .dropLastWhile { it.isEmpty() }
            .toTypedArray()
        if (logger.isLoggable(Level.FINER))
            logger.finer("creds is: " + creds[0] + ", " + creds[1])
        val username = creds[1]
        // a password is not currently required for xabber accounts
        var password = ""
        if (creds.size > 2) {
            password = creds[2]
        }
        if (logger.isLoggable(Level.FINEST))
            logger.finest("Authenticating username: $username password: [MASKED]")
        val id = "$username@${this.domain}"

        authenticated =
                authenticationProvider.authenticate(JID(id), password)

        if (authenticated) {
            logger.info("Client successfully authenticated: $username")
            jid = JID(id)
            enqueueString(CONSTANTS.AUTH.SUCCESS)
            currentState = ClientConnectionState.AUTH_SUCCESS
        } else {
            logger.severe("User failed authentication: $username")
            enqueueString(CONSTANTS.AUTH.TEMPORARY_FAIL)
            currentState = ClientConnectionState.AUTH_FAIL
        }

        // Reset the input stream after the user is authenticated, so we can handle <?xml ... ?> again
        xmlStreamReader = xmlif.createXMLStreamReader(inputStream)

    }

    @Throws(IOException::class)
    private fun negotiateTLS() {
        logger.info("wait for sendQueue to empty")
        while (!sendQueue.isEmpty()) {

            Thread.sleep(10L)
        }

        val clientKey = "nothing"
        logger.info("Converting socket for $clientKey into SSL")
        logger.info("stream Reader $xmlStreamReader")

        // try {
            val sock = transformToSSLSocket(socket)
            sock.useClientMode = false
            logger.finer("Starting ssl handshake")
            sock.startHandshake()
            logger.finer("Completed ssl handshake. ")
            // get the SSL versions jid the new socket ...
            socket = sock
            inputStream = sock.inputStream
            xmlStreamReader = xmlif.createXMLStreamReader(inputStream)
            running.set(false)
            // kicking off new sending thread
            startMessageThread()
            outputStream = sock.outputStream

            streamId = Utils.generateID(10)
            //

        // } catch (e: IOException) {
        //     logger.severe("Error getting input / output stream jid client socket (SSL).")
        //     e.printStackTrace()
        // } catch (e: Exception) {
        //     logger.severe("Error transforming socket to SSL.")
        //     e.printStackTrace()
        // }
        logger.info("converted stream Reader to ssl $xmlStreamReader")
    }

    /**
     * Call this externally to set the keystore used for the SSLSocketFactory
     * @param storeLocation the location of the keystore
     * @param trustLocation the location of the truststore
     * @param storePass keystore password
     * @param trustPass trustsore password
     */
    private fun setKeyStore(
        storeLocation: InputStream, trustLocation: InputStream,
        storePass: String, trustPass: String
    ) {
        try {
            logger.fine("Setting up keystore...")
            val context = SSLContext.getInstance("TLSv1.2")
            val keyManager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            val trustManager =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

            val keyStore = loadKeyStore(storeLocation, storePass)
            val trustStore = loadKeyStore(trustLocation, trustPass)

            keyManager.init(keyStore, XOP.SSL.PASSWORD.toCharArray())
            trustManager.init(trustStore)

            context.init(keyManager.keyManagers, trustManager.trustManagers, SecureRandom())

            socketFactory = context.socketFactory
            logger.fine("Finished setting up keystore.")
        } catch (e: NoSuchAlgorithmException) {
            logger.severe("Error while loading KeyManagerFactory: " + e.message)
        } catch (e: KeyStoreException) {
            logger.severe("Error while setting KeyStore: " + e.message)
        } catch (e: UnrecoverableKeyException) {
            logger.severe("Could not recover key: " + e.message)
        // } catch (e: KeyManagementException) {
        //     logger.severe("Error with KeyManager: " + e.message)
        }

    }

    /**
     * Do not use this, it is just a utility method for setting the keystore
     * @param location the location of the keystore
     * @return a keystore object
     */
    private fun loadKeyStore(location: InputStream?, password: String): KeyStore? {
        var store: KeyStore? = null
        try {
            store = KeyStore.getInstance(KeyStore.getDefaultType())
            if (location != null) {
                store!!.load(location, password.toCharArray())
            } else {
                logger.severe("Couldn't load null keystore")
            }
        } catch (e: KeyStoreException) {
            logger.severe("Unable to load keystore: " + e.message)
        } catch (e: CertificateException) {
            logger.severe("Unable to load keystore: " + e.message)
        } catch (e: NoSuchAlgorithmException) {
            logger.severe("Unable to load keystore: " + e.message)
        } catch (e: IOException) {
            logger.severe("Unable to load keystore: " + e.message)
        } finally {
            try {
                location?.close()
            } catch (e: IOException) {
                logger.severe("Could not close file stream: " + e.message)
            }

        }
        return store
    }

    /**
     * Transforms a conventional socket into a SSL socket.
     *
     * @param socket socket to be turned into an SSL socket
     * @return a new SSL socket or null if unable to
     * @throws IOException if unable to transform the given socket into an SSLSocket
     */
    @Throws(IOException::class)
    private fun transformToSSLSocket(socket: Socket): SSLSocket {
        if (socketFactory == null) {
            val keystorePath = File(XOP.SSL.KEYSTORE).absoluteFile
            val trustStorePath = File(XOP.SSL.TRUSTSTORE).absoluteFile
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("keystorePath: " + keystorePath.absolutePath)
            }
            setKeyStore(
                FileInputStream(keystorePath), FileInputStream(trustStorePath),
                XOP.SSL.PASSWORD, XOP.SSL.PASSWORD
            )
            if (logger.isLoggable(Level.FINE)) {
                logger.fine("using keystore: " + XOP.SSL.KEYSTORE)
                logger.fine("using truststore: " + XOP.SSL.TRUSTSTORE)
            }
        }

        if (socketFactory != null) {
            val remoteAddress = socket.remoteSocketAddress as InetSocketAddress
            logger.info("creating SSL Socket")
            logger.info("socket address: " + remoteAddress.address.hostAddress)
            val sock = socketFactory!!.createSocket(
                socket,
                remoteAddress.hostName,
                socket.port,
                true
            ) as SSLSocket
            // sock.enabledProtocols = sock.supportedProtocols.filter {
            //         protocol -> protocol != "TLSv1" && !protocol.contains("SSL") }.toTypedArray()
            sock.enabledCipherSuites = sock.supportedCipherSuites
                // .filter {
                //     protocol -> protocol.contains("ECDHE") || protocol.contains("DHE") }.toTypedArray()
            sock.enabledProtocols.asList().forEach(logger::finer)
            sock.enabledCipherSuites.asList().forEach(logger::finer)
            return sock
        } else {
            logger.severe("Couldn't open SSL Socket, the SSLServerSocketFactory was null")
            throw IOException("Couldn't open SSL Socket, the SSLServerSocketFactory was null")
        }
    }

}