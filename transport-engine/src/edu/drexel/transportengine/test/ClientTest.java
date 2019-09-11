package edu.drexel.transportengine.test;

import edu.drexel.transportengine.api.TransportEngineAPI;
import org.json.simple.JSONObject;

import java.io.IOException;

/**
 * Basic test of client functionality.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ClientTest {

    public static void main(String[] args) throws Exception {
        final TransportEngineAPI api = new TransportEngineAPI("localhost", 1998);

        api.registerMessageCallback(new TransportEngineAPI.MessageCallback() {
            @Override
            public void processMessage(JSONObject message) {
                System.out.println("Got message: " + message);
            }
        });

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    api.executeEndSession();
                } catch (IOException ex) {
                    System.err.println("Unable to end session gracefully.");
                }
            }
        });
        Runtime.getRuntime().addShutdownHook(t);

        api.start();

        api.executeChangeProperties(false, 123, false);
        api.executeSubscription("somedest", true);

        if (args.length > 0) {
            for (int i = 0; i < 5; i++) {
                api.executeSend(String.valueOf(i), "somedest", "payload goes here!");
                System.out.println("sent message");
                Thread.sleep(1000);
            }
            api.executeShutdown();
        } else {
            t.join();
        }
    }
}
