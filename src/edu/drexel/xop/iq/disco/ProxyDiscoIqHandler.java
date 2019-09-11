/**
 * (c) 2010 Drexel University
 */

package edu.drexel.xop.iq.disco;

import edu.drexel.xop.component.ComponentException;
import edu.drexel.xop.component.ComponentManager;
import edu.drexel.xop.component.VirtualComponent;
import edu.drexel.xop.core.PacketFilter;
import edu.drexel.xop.iq.IqHandler;
import edu.drexel.xop.iq.IqManager;
import edu.drexel.xop.properties.XopProperties;
import edu.drexel.xop.util.Utils;
import edu.drexel.xop.util.logger.LogUtils;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.xmpp.packet.IQ;
import org.xmpp.packet.Packet;
import org.xmpp.packet.PacketError.Condition;
import org.xmpp.packet.PacketError.Type;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>
 * This handles DISCO requests directed at this server/proxy and components<br/>
 * </p>
 * <p/>
 * 
 * @author David Millar
 */
public class ProxyDiscoIqHandler extends IqHandler implements PacketFilter {
    private Set<DiscoProvider> serverInfoProvider = wrapDiscoProvider(new ProxyDiscoProvider());
    private ComponentManager componentManager;
    public static final String ITEM_NAMESPACE = "http://jabber.org/protocol/disco#items";
    public static final String INFO_NAMESPACE = "http://jabber.org/protocol/disco#info";
    public static final String MUC_NAMESPACE = "http://jabber.org/protocol/muc";

    private static final Logger logger = LogUtils.getLogger(ProxyDiscoIqHandler.class.getName());

    public ProxyDiscoIqHandler(ComponentManager componentManager) {
        this.componentManager = componentManager;
    }

    // Handle IQ requests for Components / Server / Proxy
    // If they're addressed to an item of a component,
    // It gets sent to that component
    // Otherwise, this handler handles the request by
    // collecting items, roles, and info from the components
    public IQ handleIq(IQ iq) {
        logger.fine("handling iq: " + iq);
        Element e = iq.getChildElement();
        String ns = e.getNamespaceURI();
        if (ns.equals(ITEM_NAMESPACE)) {
            return getItems(iq);
        } else if (ns.equals(INFO_NAMESPACE)) {
            return getInfo(iq);
        } else {
            logger.log(Level.FINE, "Can't handle this IQ message");
            return IqManager.getErrorForIq(iq, Condition.bad_request, Type.cancel);
        }
    }

    // ITEMS - items
    private IQ getItems(IQ iq) {
        String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
        String subdomain = Utils.getComponentSubDomain(iq.getTo(), domain);
        VirtualComponent component = componentManager.getComponent(subdomain);

        if (component instanceof DiscoProvider) {
            logger.info("component instance of DiscoProvider");
            return getComponentItems(iq, ((DiscoProvider) component));
        }
        if (forServer(iq, domain)) {
            logger.info("Getting Server Items");
            return getServerItems(iq);
        } else {
            logger.log(Level.FINE, "Can't handle this IQ message");
            return IqManager.getErrorForIq(iq, Condition.service_unavailable, Type.cancel);
        }
    }

    // INFO - Identity and features
    private IQ getInfo(IQ iq) {
        String domain = XopProperties.getInstance().getProperty(XopProperties.DOMAIN);
        String subdomain = Utils.getComponentSubDomain(iq.getTo(), domain);
        VirtualComponent component = componentManager.getComponent(subdomain);

        if (component instanceof DiscoProvider) {
            return getComponentInfo(iq, (DiscoProvider) component);
        } else if (forServer(iq, domain)) {
            return getServerInfo(iq);
        } else {
            logger.log(Level.FINE, "Can't handle this IQ message");
            return IqManager.getErrorForIq(iq, Condition.service_unavailable, Type.cancel);
        }
    }

    // FILTER
    public boolean accept(Packet packet) {
        logger.log(Level.FINE, "Trying accept in ProxyDiscoIqHandler");
        if (!(packet instanceof IQ)) {
            return false;
        }
        IQ iq = (IQ) packet;
        if (iq.getType() != IQ.Type.get) {
            return false;
        }
        Element e = iq.getChildElement();
        if (e == null) {
            return false;
        }
        String ns = e.getNamespaceURI();
        return (ns.equals(ITEM_NAMESPACE) || ns.equals(INFO_NAMESPACE));
    }

    private IQ getServerInfo(IQ iq) {
        return assembleInfo(iq, serverInfoProvider);
    }

    private IQ getServerItems(IQ iq) {
        logger.fine("Getting server items");
        Set<DiscoItem> items = new HashSet<>();
        items.addAll(serverInfoProvider.iterator().next().getItems());
        for (VirtualComponent component : componentManager.getComponents()) {
            if (component instanceof ServerDiscoProvider) {
                items.add(((ServerDiscoProvider) component).getServerItem());
            }
        }
        return assembleServerItems(iq, items);
    }

    public static IQ getComponentItems(IQ iq, DiscoProvider component) {
        return assembleItems(iq, wrapDiscoProvider(component));
    }

    public static IQ getComponentInfo(IQ iq, DiscoProvider component) {
        return assembleInfo(iq, wrapDiscoProvider(component));
    }

    public static IQ assembleInfo(IQ iq, Set<DiscoProvider> providers) {
        IQ result = IQ.createResultIQ(iq);
        result.setChildElement("query", INFO_NAMESPACE);
        Element child = result.getChildElement();

        for (DiscoProvider provider : providers) {
            // Set<Element> elements = new HashSet<Element>();

            // Identities
            boolean hasIdentity = false;
            for (DiscoIdentity identity : provider.getIdentities()) {
                child.addElement("identity").addAttribute("category", identity.getCategory()).addAttribute("name", identity.getName()).addAttribute("type", identity.getType());
                hasIdentity = true;
            }
            // Every entity needs at least one identity
            if (!hasIdentity) {
                logger.log(Level.SEVERE, null, new ComponentException("Entities Must have at least one identity!"));
            }

            // Features
            for (String feature : provider.getFeatures()) {
                Element e = DocumentHelper.createElement("feature");
                e.addAttribute("var", feature);
                child.addElement("feature").addAttribute("var", feature);
            }
        }

        return result;
    }

    public static IQ assembleItems(IQ iq, Set<DiscoProvider> providers) {
        IQ result = IQ.createResultIQ(iq);
        result.setChildElement("query", ITEM_NAMESPACE);
        Element child = result.getChildElement();

        for (DiscoProvider provider : providers) {
            for (DiscoItem item : provider.getItems()) {
                // Element e = item.getElement().createCopy();
                // I have no idea why I can't just add e from above..
                logger.log(Level.INFO, "Adding disco item: " + item.toString());
                child.addElement("item").addAttribute("name", item.getName()).addAttribute("jid", item.getJID().toString()).addAttribute("node", item.getNode());
            }
        }
        return result;
    }

    private IQ assembleServerItems(IQ iq, Set<DiscoItem> items) {
        IQ result = IQ.createResultIQ(iq);
        result.setChildElement("query", ITEM_NAMESPACE);
        Element child = result.getChildElement();
        for (DiscoItem item : items) {
            child.addElement("item").addAttribute("name", item.getName()).addAttribute("jid", item.getJID().toString()).addAttribute("node", item.getNode());
        }
        return result;
    }

    public static Set<DiscoProvider> wrapDiscoProvider(DiscoProvider discoProvider) {
        Set<DiscoProvider> sdp = new HashSet<>();
        sdp.add(discoProvider);
        return sdp;
    }

    /**
     * Returns basic server features.
     * TODO:  We either want to (a) always load MUC, or (b) change how this function behaves based on which
     *          components are loaded.
     */
    private static class ProxyDiscoProvider implements DiscoProvider {
        Set<DiscoIdentity> identities = new HashSet<>();
        Set<String> features = new HashSet<>();
        Set<DiscoItem> items = new HashSet<>();

        public ProxyDiscoProvider() {
            identities.add(new DiscoIdentity("conference", "text", "XO Proxy"));
            features.add(MUC_NAMESPACE);
        }

        public Set<String> getFeatures() {
            return features;
        }

        public Set<DiscoIdentity> getIdentities() {
            return identities;
        }

        public Set<DiscoItem> getItems() {
            return items;
        }
    }

    private static boolean forServer(IQ iq, String serverDomain) {
        if (iq.getTo() == null) {
            return true;
        }
        String to = iq.getTo().getDomain();
        if (serverDomain.equals(to)) {
            return true;
        }
        // also check if it's any valid network address for the local machine

        try {
            InetAddress addr = InetAddress.getByName(iq.getTo().toString());
            if (NetworkInterface.getByInetAddress(addr) != null) {
                return true;
            }
        } catch (UnknownHostException | SocketException e) {
            logger.log(Level.FINE, "I cannot understand this packet's \"to\" field: " + iq);
        }

        return false;
    }
}