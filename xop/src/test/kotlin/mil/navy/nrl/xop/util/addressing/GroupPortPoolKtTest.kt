package mil.navy.nrl.xop.util.addressing

import org.junit.jupiter.api.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupPortPoolKtTest {

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
    }

    @Test fun getPort() {
        var portRange: String = "10000"   // = "10000-20000"
        var str = "room@conference.proxy"
        var port = getPort(str,  10000, 20000)
        println("str $str => port $port")
        Assertions.assertTrue( port in 10000 .. 20000, "port is actually $port")

        port = getPort(str,  10000, 20000)
        println("str $str => port $port")

        port = getPort(str,  10000, 10000)
        println("range $portRange, str $str, port: $port")
        Assertions.assertEquals(port, 10000)


        portRange = "10000-20000"
        val str2 = "room2@conference.proxy"
        val port2 = getPort(str2,  10000, 20000)
        port = getPort(str,  10000, 20000)
        println("range $portRange, str2 $str2, port2 $port2")
        Assertions.assertNotEquals(port, port2, "$port == $port2")

        portRange = "10000-10002"
        println("range $portRange, str $str: ${getPort(str,  10000, 10002)}")
        Assertions.assertTrue( port in 10000 .. 20000, "port is actually $port")

        println("range $portRange:: ${getPort("a@b.y",  10000, 10002)}")
        println("range $portRange:: ${getPort("a@asdb.axy", 10000, 10002)}")

//        portRange = "10000-20000"
        str = "ONETOONE"
        port = getPort(str , 10000, 20000)
        Assertions.assertTrue( port in 10000 .. 20000, "port is actually $port")
    }

    @Test fun getNodeId() {
        val nodeId: Long = getNodeId("10.0.1.15/")
        assert(nodeId == 115L)
    }
}