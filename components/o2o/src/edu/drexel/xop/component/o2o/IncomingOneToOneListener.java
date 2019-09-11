package edu.drexel.xop.component.o2o;

import edu.drexel.xop.util.logger.LogUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Logger;

/**
 * 
 * @author urlass
 *
 * Class that listens for incoming one-to-one chat messages.
 */
public class IncomingOneToOneListener extends Thread {
	
	int DEFAULT_PORT = 5562;
	int port = DEFAULT_PORT;
	private static final Logger logger = LogUtils.getLogger(IncomingOneToOneListener.class.getName());
	public IncomingOneToOneListener(){

	}
	
	public IncomingOneToOneListener(int port){
		this.port = port;
	}
	
	public void run(){
		boolean done = false;

		//start the listening socket
		ServerSocket ss = null;
		try {
			ss = new ServerSocket(this.port);
		} catch (IOException e) {
			e.printStackTrace();
			done = true;
		}
		
		while(!done){

			logger.fine("Listening for incoming one-to-one chats on port " + this.DEFAULT_PORT + ".");

			Socket socket = null;
			try {
				socket = ss.accept();
			} catch (IOException e) {
				e.printStackTrace();
				done=true;
			}

			logger.info("Received incoming one-to-one chat connection.");
			
			//when a connection comes in, kick off a handler thread
			OneToOneHandler o2oh = new OneToOneHandler(socket);
			o2oh.start();
		}
	}
}