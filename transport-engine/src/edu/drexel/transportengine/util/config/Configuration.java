package edu.drexel.transportengine.util.config;

import java.io.*;
import java.util.*;

/**
 * Helper class for reading a configuration file.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public final class Configuration {

    private static Configuration instance;
    private CommentedProperties props;

    /**
     * Private constructor so this is a singleton.
     */
    private Configuration() {
        props = new CommentedProperties();
        for (TEProperties tep : TEProperties.values()) {
            props.setProperty(tep.getKey(), tep.getDefaultValue(), tep.getComment());
        }
    }

    public void loadFile(String path) throws IOException {
        FileInputStream in = new FileInputStream(path);
        props.load(in);
        in.close();
    }

    /**
     * Gets a value as a string.
     *
     * @param key the key.
     * @return the value.
     */
    public String getValueAsString(TEProperties key) {
        return props.getProperty(key.getKey());
    }

    /**
     * Gets a value as a string array.
     *
     * @param key the key.
     * @return the value.
     */
    public String[] getValueAsArray(TEProperties key) {
        return getValueAsString(key).split(",");
    }

    /**
     * Gets a value as an int.
     *
     * @param key the key.
     * @return the value.
     */
    public int getValueAsInt(TEProperties key) {
        return Integer.parseInt(getValueAsString(key));
    }

    /**
     * Gets a value as a long.
     *
     * @param key the key.
     * @return the value.
     */
    public long getValueAsLong(TEProperties key) {
        return Long.parseLong(getValueAsString(key));
    }

    /**
     * Gets a value as a boolean.
     *
     * @param key the key.
     * @return the value.
     */
    public boolean getValueAsBool(TEProperties key) {
        return getValueAsString(key).toLowerCase().equals("true");
    }

    public void wrtieToFile(String path) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(path));
        props.store(out, "");
        out.close();
    }

    /**
     * Gets the configuration instance.
     *
     * @return the configuration instance.
     */
    public static Configuration getInstance() {
        if (instance == null) {
            instance = new Configuration();
        }
        return instance;
    }

    private static final class CommentedProperties extends Properties {
        private Map<String, String> comments;
        private List<String> ordered;

        public CommentedProperties() {
            comments = new HashMap<>();
            ordered = new LinkedList<>();
        }

        public Object setProperty(String key, String value, String comment) {
            ordered.add(key);
            comments.put(key, comment);
            return super.setProperty(key, value);
        }

        @Override
        public void store(Writer writer, String headerComments) throws IOException {
            writer.write(headerComments);
            String last = null;
            for (String prop : ordered) {
                if (last != null && prop.charAt(0) != last.charAt(0)) {
                    writer.write("\n");
                }
                if (comments.containsKey(prop)) {
                    writer.write("# " + comments.get(prop) + "\n");
                }
                writer.write(prop + "=" + getProperty(prop) + "\n");
                last = prop;
            }
        }
    }
}
