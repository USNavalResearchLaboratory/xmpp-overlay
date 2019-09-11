package edu.drexel.transportengine.components.contentstore;

import com.almworks.sqlite4java.SQLiteConnection;
import com.almworks.sqlite4java.SQLiteException;
import com.almworks.sqlite4java.SQLiteStatement;
import edu.drexel.transportengine.components.Component;
import edu.drexel.transportengine.core.TransportEngine;
import edu.drexel.transportengine.core.TransportProperties;
import edu.drexel.transportengine.core.events.ApplicationMessage;
import edu.drexel.transportengine.core.events.ApplicationMessage.MessageUID;
import edu.drexel.transportengine.core.events.Event;
import edu.drexel.transportengine.util.config.Configuration;
import edu.drexel.transportengine.util.config.TEProperties;
import edu.drexel.transportengine.util.logging.LogUtils;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Non-volatile storage for <code>ApplicationMessage</code> events.  This class uses an underlying
 * sqlite3 database for storage.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class ContentStoreComponent extends Component {

    private final Logger logger = LogUtils.getLogger(this.getClass().getName());
    private SQLiteStatement selectAllStmt;
    private SQLiteStatement selectStmt;
    private SQLiteStatement insertStmt;
    private SQLiteStatement deleteStmt;
    private SQLiteStatement purgeStmt;
    private LimitedCache<MessageUID, Event<ApplicationMessage>> cache;
    private boolean cacheDirty;

    /**
     * Instantiates a new content store.
     *
     * @param engine reference to the Transport Engine.
     * @throws SQLiteException if the database could not be created.
     */
    public ContentStoreComponent(TransportEngine engine) throws SQLiteException {
        super(engine);

        cache = new LimitedCache<>();
        cacheDirty = true;

        Logger.getLogger("com.almworks.sqlite4java").setLevel(Level.OFF);
        SQLiteConnection db = new SQLiteConnection(new File(
                Configuration.getInstance().getValueAsString(TEProperties.CONTENTSTORE_PATH)
                        + "/messages-" + engine.getGUID() + ".db"));
        db.open();

        SQLiteStatement stmt = db.prepare(
                "CREATE TABLE IF NOT EXISTS messages ("
                        + "src_engine VARCHAR(255) NOT NULL,"
                        + "src_client VARCHAR(255) NOT NULL,"
                        + "id VARCHAR(255) NOT NULL,"
                        + "version INTEGER NOT NULL,"
                        + "dest VARCHAR(255) NOT NULL,"
                        + "payload BLOB,"
                        + "reliable BOOLEAN NOT NULL,"
                        + "persist_until INTEGER NOT NULL,"
                        + "ordered BOOLEAN NOT NULL,"
                        + "UNIQUE (src_engine, src_client, id))");
        stmt.stepThrough();
        stmt.dispose();

        selectAllStmt = db.prepare("SELECT * FROM messages");
        selectStmt = db.prepare("SELECT * FROM messages WHERE src_engine = ? AND src_client = ? AND id = ?");
        insertStmt = db.prepare("INSERT INTO messages "
                + "(src_engine, src_client, id, version, dest, payload, reliable, persist_until, ordered)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)");
        deleteStmt = db.prepare("DELETE FROM messages WHERE src_engine = ? AND src_client = ? AND id = ?");
        purgeStmt = db.prepare("DELETE FROM messages WHERE persist_until >= 0 AND persist_until < ?");

        if (Configuration.getInstance().getValueAsBool(TEProperties.CONTENTSTORE_RESET)) {
            logger.info("Database reset.");
            db.prepare("DELETE FROM messages").stepThrough();
        }
    }

    /**
     * Retrieves a message event given a globally unique <code>MessageUID</code>.
     *
     * @param uid the UID to retrieve.
     * @return the event associated with <code>uid</code>.  If it does not exist, this function
     *         returns <code>null</code>.
     */
    public synchronized Event<ApplicationMessage> selectMessage(MessageUID uid) {
        purgeOld();
        if (cache.containsKey(uid)) {
            return cache.get(uid);
        }

        Event<ApplicationMessage> event = null;
        try {
            selectStmt.bind(1, uid.getSrcEngine()).bind(2, uid.getSrcClient()).bind(3, uid.getId());

            if (selectStmt.step()) {
                ApplicationMessage message = new ApplicationMessage(
                        selectStmt.columnString(0),
                        selectStmt.columnString(1),
                        selectStmt.columnString(2),
                        selectStmt.columnInt(3),
                        selectStmt.columnString(5));
                TransportProperties tp = new TransportProperties(
                        selectStmt.columnString(6).equals("true"),
                        selectStmt.columnInt(7),
                        selectStmt.columnString(8).equals("true"));
                event = new Event<>(selectStmt.columnString(4), tp, message);
            }
        } catch (SQLiteException ex) {
            logger.warning("Unable to select: " + ex.getMessage());
        } finally {
            try {
                selectStmt.reset();
            } catch (SQLiteException ex) {
                logger.warning("Unable to cleanup after failed SELECT");
            }

            if (event != null) {
                cache.put(event.getContents().getUID(), event);
            }
        }
        return event;
    }

    public synchronized List<Event<ApplicationMessage>> selectAll() {
        purgeOld();
        if (!cacheDirty) {
            return new LinkedList<>(cache.values());
        }

        Event<ApplicationMessage> event = null;
        LinkedList<Event<ApplicationMessage>> events = new LinkedList<>();
        try {
            while (selectAllStmt.step()) {
                ApplicationMessage message = new ApplicationMessage(
                        selectAllStmt.columnString(0),
                        selectAllStmt.columnString(1),
                        selectAllStmt.columnString(2),
                        selectAllStmt.columnInt(3),
                        selectAllStmt.columnString(5));
                TransportProperties tp = new TransportProperties(
                        selectAllStmt.columnString(6).equals("true"),
                        selectAllStmt.columnInt(7),
                        selectAllStmt.columnString(8).equals("true"));
                event = new Event<>(selectAllStmt.columnString(4), tp, message);
                events.add(event);
            }
        } catch (SQLiteException ex) {
            logger.warning("Unable to select: " + ex.getMessage());
        } finally {
            try {
                selectAllStmt.reset();
            } catch (SQLiteException ex) {
                logger.warning("Unable to cleanup after failed SELECT");
            }

            if (event != null) {
                cache.put(event.getContents().getUID(), event);
            }
        }
        return events;
    }

    /**
     * Inserts an event into the content store.
     *
     * @param message the message to insert.
     */
    public synchronized void insertMessage(Event<ApplicationMessage> message) {
        ApplicationMessage contents = message.getContents();
        try {
            // TODO: This should be atomic, but using BEGIN TRANSACTION seems to lock 
            // the table.
            // TODO: Check for uniqueness.
            deleteMessage(contents.getUID());

            insertStmt.bind(1, contents.getUID().getSrcEngine())
                    .bind(2, contents.getUID().getSrcClient())
                    .bind(3, contents.getUID().getId())
                    .bind(4, contents.getVersion())
                    .bind(5, message.getDest() == null ? "" : message.getDest())
                    .bind(6, contents.getPayload())
                    .bind(7, message.getTransportProperties().reliable ? "true" : "false")
                    .bind(8, message.getTransportProperties().persistentUntil)
                    .bind(9, message.getTransportProperties().ordered ? "true" : "false");
            insertStmt.stepThrough();
            insertStmt.reset();
            cache.put(contents.getUID(), message);
            cacheDirty = true;
        } catch (SQLiteException ex) {
            logger.warning("Unable to insert: " + ex.getMessage());
        }
    }

    /**
     * Deletes an event from the content store.
     *
     * @param uid event to delete.
     */
    public synchronized void deleteMessage(MessageUID uid) {
        try {
            deleteStmt.bind(1, uid.getSrcEngine()).bind(2, uid.getSrcClient()).bind(3, uid.getId());
            deleteStmt.stepThrough();
            deleteStmt.reset();
            cache.remove(uid);
        } catch (SQLiteException ex) {
            logger.warning("Unable to delete: " + ex.getMessage());
        }
    }

    private synchronized void purgeOld() {
        try {
            // Delete the entries in the database
            long current = System.currentTimeMillis() / 1000L;
            purgeStmt.bind(1, current);
            purgeStmt.stepThrough();
            purgeStmt.reset();

            // Clear the entries from the cache.
            for (Iterator<Event<ApplicationMessage>> it = cache.values().iterator(); it.hasNext(); ) {
                Event<ApplicationMessage> event = it.next();
                int until = event.getTransportProperties().persistentUntil;
                if(until >= 0 && until < current) {
                    it.remove();
                }
            }
        } catch (SQLiteException ex) {
            logger.warning("Unable to purge: " + ex.getMessage());
        }
    }

    /**
     * Receives <code>ApplicationMessage</code> events and inserts them into the content store.
     * Also, this function receives <code>QueryMessageEvent</code>s and responds with the associated
     * event.
     *
     * @param event the event.
     */
    @Override
    public void handleEvent(Event event) {
        if (event.getContents() instanceof ApplicationMessage
                && event.getTransportProperties().persistentUntil > 0) {
            insertMessage((Event<ApplicationMessage>) event);
        } else if (event.getContents() instanceof QueryMessageEvent) {
            QueryMessageEvent query = (QueryMessageEvent) event.getContents();
            Event<ApplicationMessage> ret = selectMessage(query.getUID());

            ((Event<QueryMessageEvent>) event).getContents().setMessage(ret);
            event.setResponded();
        } else if (event.getContents() instanceof QueryAllMessagesEvent) {
            List<Event<ApplicationMessage>> events = selectAll();
            ((QueryAllMessagesEvent) event.getContents()).setMessages(events);
            event.setResponded();
        }
    }

    private class LimitedCache<A, B> extends LinkedHashMap<A, B> {
        @Override
        protected boolean removeEldestEntry(Map.Entry<A, B> eldest) {
            return size() >= Configuration.getInstance().getValueAsInt(TEProperties.CONTENTSTORE_CACHE_LIMIT);
        }
    }
}
