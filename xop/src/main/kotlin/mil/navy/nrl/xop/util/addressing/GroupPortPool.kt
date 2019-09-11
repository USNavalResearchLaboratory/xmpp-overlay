package mil.navy.nrl.xop.util.addressing

import edu.drexel.xop.util.logger.LogUtils
import java.util.logging.Logger

val logger: Logger = LogUtils.getLogger("mil.navy.nrl.xop.util.addressing.GroupPortPoolkt")

fun parseInt(str: String): Int {
    return str.toInt()
}

fun getPort(str: String, startPort: Int, endPort: Int): Int {
    val hashcode = Math.abs(str.hashCode())
    val port: Int

    //val (startPort, endPort) = extractPortRange(portRange)
    val range = endPort - startPort + 1
    port = (hashcode % range) + startPort
    logger.fine("str $str => hashcode $hashcode. over range $range ==> port $port")

    return port
}

data class PortRange(val startPort: Int, val endPort: Int)

fun extractPortRange(portRange: String): PortRange {
    val portList = portRange.split("-")

    val startPort = parseInt(portList.first())
    val endPort = parseInt(portList.last())
    return PortRange(startPort, endPort)
}

fun getNodeId(bindAddressStr: String): Long {
    logger.finest("bindAddressStr $bindAddressStr")
    val octets = bindAddressStr.split(".")
    logger.finest("octets: ${octets.toList()}")
    val lastTwo: String = octets[octets.size - 2] + octets[octets.size - 1]
    //String noDots =bindAddressStr.replace(".","");
    return lastTwo.replace("/", "").toLong()
}


fun main(args: Array<String>) {
    var str = "room@conference.proxy"
    var port = getPort(str, 10000, 20000)
    println("str $str => port $port")

    str = "room2@conference.proxy"
    port = getPort(str, 10000, 20000)
    println("str $str => port $port")

    var portRange = "10000"
    println("range $portRange, str $str: ${getPort(str, 10000, 20000)}")

    portRange = "10000-10000"
    println("range $portRange, str $str: ${getPort(str, 10000, 20000)}")
    portRange = "10000-10001"
    println("range $portRange, str $str: ${getPort(str, 10000, 20000)}")
    portRange = "10000-10002"
    println("range $portRange, str $str: ${getPort(str, 10000, 20000)}")
    println("range $portRange:: ${getPort("a@b.y", 10000, 20000)}")
    println("range $portRange:: ${getPort("a@asdb.axy", 10000, 20000)}")
}
