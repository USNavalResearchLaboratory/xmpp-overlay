package edu.drexel.transportengine.components.clientmanager;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

/**
 * Utility for easily reading <code>JSONObject</code>s.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class JSONReader {

    private JSONObject json;

    /**
     * Instantiates a <code>JSONReader</code> from a string.
     *
     * @param json JSON string.
     */
    public JSONReader(String json) {
        this.json = (JSONObject) JSONValue.parse(json);
    }

    /**
     * Instantiates a <code>JSONReader</code> from a <code>JSONObject</code>.
     *
     * @param json JSON object.
     */
    public JSONReader(JSONObject json) {
        this.json = json;
    }

    /**
     * Determines if the JSON contains a given key.
     *
     * @param key key to check.
     * @return if the JSON contains <code>key</code>.
     */
    public boolean hasValue(String key) {
        return json.containsKey(key);
    }

    /**
     * Retrieves the value associated with <code>key</code> as a <code>JSONObject</code>.
     *
     * @param key key to access.
     * @return the associated value.
     */
    public JSONObject asJSON(String key) {
        return (JSONObject) json.get(key);
    }

    /**
     * Retrieves the value associated with <code>key</code> as a <code>String</code>.
     *
     * @param key key to access.
     * @return the associated value.
     */
    public String asString(String key) {
        return json.get(key).toString();
    }

    /**
     * Retrieves the value associated with <code>key</code> as a <code>boolean</code>.
     *
     * @param key key to access.
     * @return the associated value.
     */
    public boolean asBoolean(String key) {
        return asString(key).equals("true");
    }

    /**
     * Retrieves the value associated with <code>key</code> as a <code>int</code>.
     *
     * @param key key to access.
     * @return the associated value.
     */
    public int asInt(String key) {
        return Integer.parseInt(asString(key));
    }

    /**
     * Gets the underlying JSONObject as a <code>String</code>.
     *
     * @return the JSONObject as a <code>String</code>.
     */
    @Override
    public String toString() {
        return json.toJSONString();
    }
}
