package edu.drexel.xop.component.xog;

import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.logger.LogUtils;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smackx.muc.MultiUserChat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;

/**
 * This thread helps the ElecetedLeaderGateway class by listening in a chat room,
 * and sending updates.  Every X seconds, it will tell the ElectedLeaderGateway whether
 * or not it is now the gateway.
 * 
 * A summary of how this works is that if it receives an ID that is greater than its own, during 
 * the cycle, it does not act as a gateway.  Otherwise it does.  It does not keep track of who
 * the gateway is, since we don't need that functionality for our use case.
 * 
 * @author urlass
 *
 */
public class LeaderElectionCommunicator extends Thread implements PacketListener{

    private static final Logger logger = LogUtils.getLogger(LeaderElectionCommunicator.class.getName());
	int largestNumber = 0;
	int myId;
    private String username;
	private ElectedLeaderGateway elg;
	
	LeaderElectionCommunicator(int my_id, ElectedLeaderGateway my_boss){
		this.myId = my_id;
		username = Integer.toString(my_id);
		elg = my_boss;
	}
	
	public void Run(){
		//first, connect to the local proxy
		MultiUserChat chat = null;
		try {
            String PASSWORD = "a";
            String RESOURCE = "gateway.election.agent";
            chat = connectToChat(username, PASSWORD, InetAddress.getLocalHost().toString(), XopProperties.getInstance().getProperty(XopProperties.GATEWAY_CONTROL_CHANNEL), RESOURCE);
		} catch (UnknownHostException e) {
			logger.severe("Unable to resolve local host!  This will cause the gateway to fail!");
			e.printStackTrace();
		}
		//add ourselves as a message listener, so that processPacket will get called when a message comes in
		chat.addMessageListener(this);
		
		while(true){
			//wait for the specified number of seconds
			try {
				sleep(Long.parseLong(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_ELECTION_TIME_PERIOD)));
			} catch (NumberFormatException e1) {
				logger.severe("Unable to parse " + XopProperties.GATEWAY_ELECTION_TIME_PERIOD + ".  This is supposed to be of type long.");
				e1.printStackTrace();
			} catch (InterruptedException e1) {
				logger.severe("Unable to sleep.");
				e1.printStackTrace();
			}
			
			//send out my ID
			Message msg = new Message(XopProperties.getInstance().getProperty(XopProperties.GATEWAY_CONTROL_CHANNEL), Message.Type.groupchat);
			msg.setBody(Integer.toString(this.myId));
			try{
				chat.sendMessage(msg);
			}catch(XMPPException e){
				logger.severe("Unable to send gateway election message!");
			}
		}
	}

	@Override
	public void processPacket(Packet p) {
		if(p.getClass().equals(Message.class)){
			Message m = (Message)p;
			String body = m.getBody();
			if(Integer.parseInt(body) > myId){
				elg.setAmGateway(true);
			}else{
				elg.setAmGateway(false);
			}
		}
	}
	
	 private MultiUserChat connectToChat(String username, String password, String server, String chatroom, String resource) {
	        System.err.println(System.currentTimeMillis() + " creating new connection to server: " + server);
	        // create the connection to the XMPP server
         XMPPConnection connection = new XMPPConnection(server);

	        //probably overkill to retry if we can't connect, since this is connecting to localhost...
	        System.err.println(System.currentTimeMillis() + " server = " + server);
	        while (!connection.isConnected()) {
	            System.err.println("not connected: connect again");
	            try {
	                connection.connect();
	            } catch (XMPPException e) {
	                System.err.println("Connection exception caught!");
	                e.printStackTrace();
	                System.err.println(e.getStreamError());
	                System.err.println("Will try again later.");
	                try {
	                    Thread.sleep(1000);
	                } catch (Exception f) {
	                    System.err.println("I can't get any sleep!");
	                    f.printStackTrace();
	                    System.exit(0);
	                }
	            }
	        }
	        while (!connection.isAuthenticated()) {
	            // login to the XMPP server
	            try {
	                if (!connection.isConnected())
	                    connection.connect();
	                connection.login(username, password, resource);
	                System.err.println("u,p= " + username + "," + password);
	            } catch (Exception e) {
	                System.err.println("Login exception caught!");
	                e.printStackTrace();
	                System.err.println(e.getMessage());
	            }
	        }
	        // join the chatroom
	        MultiUserChat muc = new MultiUserChat(connection, chatroom);
	        System.err.println("chatroom = " + chatroom);
	        try {
	            muc.join(username, null, null, 20000);

	        } catch (XMPPException e) {
	            System.err.println("Error connecting to chatroom (XMPP Exception).  The most common causes of this error is entering the chatroom name incorrectly.  Ensure that it is in the format room@hostname.com.");
	            e.printStackTrace();
	            System.exit(-1);
	        }

	        // add our class that processes other clients' muc messages
	        //ChatRoomListener crl = new ChatRoomListener(this);
	        //muc.addMessageListener(crl);

	        System.out.println("No errors here!");

	        return muc;
	 }
}
