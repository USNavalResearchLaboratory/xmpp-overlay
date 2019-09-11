package mil.navy.nrl.xop.util.addressing

import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import java.util.logging.Level

fun getBindAddress(ifaceName: String): InetAddress {
    val interfaces = ifaceName.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
    for (ifaceStr in interfaces) {
        if (logger.isLoggable(Level.FINER)) logger.finer("using interface: $ifaceStr")

        val ni = NetworkInterface.getByName(ifaceStr)
            ?: throw SocketException("Cannot find the specified interface: $ifaceStr")

        for (ifaceAddr in ni.interfaceAddresses) {
            if (ifaceAddr.address is Inet4Address) {
                if (logger.isLoggable(Level.FINER))
                    logger.finer("bind address: $ifaceAddr")
                return ifaceAddr.address
            }
        }
    }
    throw SocketException("No interfaces passed in $ifaceName")
}


//fun meaninglessCounter(): Int {
//    var counter = 0
//    for (i in 1..10_000_000_000) {
//        counter += 1
//    }
//    return counter
//}
//
//fun main(args: Array<String>) = runBlocking{
//    // Sequential execution.
//    val time = measureTimeMillis {
//        val one = meaninglessCounter()
//        val two = meaninglessCounter()
//        println("The answer is ${one + two}")
//    }
//    println("Sequential completed in $time ms")
//
//    // Concurrent execution.
//    val time2 = measureTimeMillis {
//        val one = async { meaninglessCounter() }
//        val two = async { meaninglessCounter() }
//        runBlocking {
//            println("The answer is ${one.await() + two.await()}")
//        }
//    }
//    println("Concurrent completed in $time2 ms\n")
//}
