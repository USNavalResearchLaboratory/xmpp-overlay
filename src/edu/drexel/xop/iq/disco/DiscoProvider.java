/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq.disco;

import java.util.Set;

/**
 * Components that have associated features, identities, and Disco items<br/>
 * Should implement this interface.  If they don't, a generic "feature not supported"<br/>
 * Response is sent to the client.  When a DiscoProvider is added to the<br/>
 * Component Manager, ComponentManager and ProxyDiscoIqHandler take care of the rest.
 *
 * @author David Millar
 */
public interface DiscoProvider {

    /**
     * Returns server/proxy features
     *
     * @return features as strings
     */
    public Set<String> getFeatures();

    /**
     * Element for identities of this provider
     *
     * @return entities
     */
    public Set<DiscoIdentity> getIdentities();

    /**
     * Returns the set of Items associated with this DiscoProvider
     *
     * @return items
     */
    public Set<DiscoItem> getItems();
}
