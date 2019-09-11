package edu.drexel.transportengine.test;

import edu.drexel.transportengine.components.contentstore.QueryAllMessagesEvent;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.Event;

/**
 * Basic test of content store purge.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class PurgeTest {

    public static void main(String[] args) throws Exception {
        TransportEngine te = new TransportEngine("purge");
        te.start();

        int dur = 2;
        Event<ApplicationMessage> e = new Event<>(
                "wherever",
                new TransportProperties(false, (int) (System.currentTimeMillis() / 1000L) + dur, false),
                new ApplicationMessage(te.getGUID(), "1", "123", 1, "this is a test"));
        te.executeEvent(e);

        Event<QueryAllMessagesEvent> qe;

        for (int i = 0; i < 5; i++) {
            qe = new Event<>(new QueryAllMessagesEvent());
            te.executeEventAwaitResponse(qe);
            System.out.println(i + " " + qe.getContents().getMessages().size());
            Thread.sleep(1000);
        }
    }
}
