/**
 * 
 */
package edu.drexel.xop.component.s2s;

import static org.junit.Assert.assertTrue;

import java.util.HashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xmpp.packet.JID;
import org.xmpp.packet.Message;
import org.xmpp.packet.Presence;

import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.xop.net.XopNet;
import edu.drexel.xop.net.discovery.GroupDiscoverableObject;
import edu.drexel.xop.net.discovery.MembershipDiscoverableObject;
import edu.drexel.xop.net.discovery.OccupantStatus;
import edu.drexel.xop.util.logger.LogUtils;

/**
 * @author duc
 *
 */
public class S2SPresenceMonitorTest {
    private Presence p;
    private Message m;
    
    private JID to;
    private JID from;
    
    S2SPresenceMonitor pm;
    GroupDiscoverableObject roomgdo;
    MembershipDiscoverableObject user1mdo ;
    MembershipDiscoverableObject gwusermdo;
    MembershipDiscoverableObject enterpriseusermdo ;
    
    private static TransportEngine engine;
    
//    @BeforeClass
//    public static void startTE(){
//        if( engine == null ){
//            //start transport engine.
////            Configuration.getInstance().loadConfiguration("filename");
//            engine = new TransportEngine("S2SPresenceMonitorTest");
//            engine.start();
//        }       
//    }
   
    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        LogUtils.loadLoggingProperties();
        
        p = new Presence();
        
        to = new JID("room@conference.n4gw/user1");
        from = new JID("room@conference.n4gw/enterpriseuser");
        
        m = new Message();
        
        pm = new S2SPresenceMonitor(new HashSet<String>());
        XopNet.getInstance();
        
        roomgdo = new GroupDiscoverableObject(XopNet.getSDObject(),"room" );
        user1mdo = new MembershipDiscoverableObject(
                XopNet.getSDObject(), "user1@proxy",
                "room@conference.proxy/user1","user1",
                "room",
                OccupantStatus.AVAILABLE);
        gwusermdo = new MembershipDiscoverableObject(
                XopNet.getSDObject(), "gwuser@proxy",
                "room@conference.proxy/gwuser","gwuser",
                "room",
                OccupantStatus.AVAILABLE);
        enterpriseusermdo = new MembershipDiscoverableObject(
                XopNet.getSDObject(), "enterpriseuser@n4gw",
                "room@conference.proxy/enterpriseuser","enterpriseuser",
                "room",
                OccupantStatus.AVAILABLE);
        pm.groupAdded(roomgdo);
        pm.membershipAdded(user1mdo);
        pm.membershipAdded(gwusermdo);
    }

    /**
     * @throws java.lang.Exception
     */
    @After
    public void tearDown() throws Exception {
        pm = null;
        
        //XopNet.getInstance().stop();
        //shutdown transport engine.
    }

    /**
     * Test method for {@link edu.drexel.xop.component.s2s.S2SPresenceMonitor#isPacketFromEnterpriseClient(org.xmpp.packet.Presence)}.
     */
    @Test
    public void testIsEnterpriseClient() {
        p.setTo(to);
        p.setFrom(from);
        p.setStatus("AVAILABLE");
        
        pm.membershipAdded(enterpriseusermdo);
        
        boolean isFromEnterprise = pm.isPresenceFromEnterpriseClient(p);
        System.out.println("isFromEnterprise: "+isFromEnterprise);
        assertTrue("not true", isFromEnterprise);
    }

    
    /**
     * Test method for {@link edu.drexel.xop.component.s2s.S2SPresenceMonitor#shouldModifyAndForwardPacket(org.xmpp.packet.Packet)}.
     */
    @Test
    public void testShouldModifyAndForwardPacket() {
        m.setTo(to);
        m.setFrom(from);
        m.setBody("body");
        
        boolean shouldModifyPacket = pm.shouldModifyAndForwardPacket(m);
        
        System.out.println("shouldModifyAndForwardPacket: "+shouldModifyPacket);
        assertTrue("not true",shouldModifyPacket);
    }


}
