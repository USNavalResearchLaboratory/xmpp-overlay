/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.properties;

/**
 * Classes implementing this interface will be notified immediately when properties
 * are set or deleted.
 *
 * @author David Millar
 */
public interface PropertyEventListener {
    public void propertySet(String key, String oldValue);

    public void propertyDeleted(String key, String oldValue);
}
