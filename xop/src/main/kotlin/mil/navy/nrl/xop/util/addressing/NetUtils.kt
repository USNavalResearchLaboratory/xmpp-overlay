package mil.navy.nrl.xop.util.addressing

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.logging.Level

fun getBindAddresses(ifaceList: String): Array<InetAddress> {
    val interfaces = ifaceList.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    val retVal: MutableList<InetAddress> = mutableListOf()
    for (ifaceStr in interfaces) {
        if (logger.isLoggable(Level.FINER)) logger.finer("using interface: $ifaceStr")

        val ni = NetworkInterface.getByName(ifaceStr)
            ?: throw SocketException("Cannot find the specified interface: $ifaceStr")

        for (ifaceAddr in ni.interfaceAddresses) {
            if (ifaceAddr.address is Inet4Address) {
                if (logger.isLoggable(Level.FINER))
                    logger.finer("bind address: $ifaceAddr")
                retVal.add(ifaceAddr.address)
            }
        }
    }
    return retVal.toTypedArray()
}

fun getBindAddress(ifaceName: String): InetAddress {
    if (logger.isLoggable(Level.FINER)) logger.finer("using interface: $ifaceName")

    val ni = NetworkInterface.getByName(ifaceName)
        ?: throw SocketException("Cannot find the specified interface: $ifaceName")

    for (ifaceAddr in ni.interfaceAddresses) {
        if (ifaceAddr.address is Inet4Address) {
            if (logger.isLoggable(Level.FINER))
                logger.finer("bind address: $ifaceAddr")
            return ifaceAddr.address
        }
    }
    throw SocketException("No addresses for $ifaceName")
}
