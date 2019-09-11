package edu.drexel.xop.net.discovery;

import mil.navy.nrl.protosd.api.distobejcts.SDPropertyValues;
import edu.drexel.xop.properties.XopProperties;

/**
 * Maps from external XOp properties to variables used in this package... This is meant to
 * (eventually) provide a class that can be used to instantiate one instance of
 * SDInstance so we can have multiple objects running different modes.
 */
public class XOPPropertyValues extends SDPropertyValues {

    private int portp2pj = 5250;

    public XOPPropertyValues() {
        System.err.println("SDPropertyValues.<init> - creating");
        XopProperties props = XopProperties.getInstance();

        super.setDomain(XopProperties.getInstance().getProperty(XopProperties.DOMAIN));
        // xop.domain=proxy
        super.setMulticastPort(props.getIntProperty(XopProperties.MUC_PORT, 5250));

        super.setTtl(props.getLongProperty(XopProperties.INDI_TTL));
        super.setRai(props.getLongProperty(XopProperties.INDI_RAI));
        super.setNumra(props.getIntProperty(XopProperties.INDI_NUMRA));

        super.setCgiStr(props.getProperty(XopProperties.INDI_CQI));

        super.setSci(props.getLongProperty(XopProperties.INDI_SCI));
        super.setNumscm(props.getIntProperty(XopProperties.INDI_NUMSCM));

        // Ian's additions - only set these if people upgrade and set the defaults in their properties file.

        if (props.getProperty(XopProperties.INDI_LOOPBACK) != null) {
            super.setLoopback(props.getBooleanProperty(XopProperties.INDI_LOOPBACK));
        }

        if (props.getProperty(XopProperties.INDI_DUPLICATES) != null) {
            super.setDuplicates(props.getBooleanProperty(XopProperties.INDI_DUPLICATES));
        }
    }

    public int getPortp2pj() {
        return portp2pj;
    }

}
