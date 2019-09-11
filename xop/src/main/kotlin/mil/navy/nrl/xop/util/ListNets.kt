package mil.navy.nrl.xop.util

import java.net.NetworkInterface

fun main(args: Array<String>) {
    for (netInt in NetworkInterface.getNetworkInterfaces())
        displayInterfaceInformation(netInt)
}


fun displayInterfaceInformation(netIface: NetworkInterface) {
    println("Display name: ${netIface.displayName}")
    println("Name: ${netIface.name}")
    for (inetAddress in netIface.inetAddresses) {
        println("InetAddress: $inetAddress")
    }
    println("")
}