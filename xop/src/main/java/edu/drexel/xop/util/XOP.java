package edu.drexel.xop.util;

/**
 * Convenience Interface for holding values used throughout XOP
 */
public interface XOP {

    String DOMAIN = XopProperties.getProperty(XOPKEYS.DOMAIN);
    String CONFERENCE_SUBDOMAIN = XopProperties.getProperty(XOPKEYS.CONFERENCE_SUBDOMAIN);
    int PORT = XopProperties.getIntProperty(XOPKEYS.PORT);
    int MUCPORT = XopProperties.getIntProperty(XOPKEYS.MUCPORT);

    interface BIND {
        String INTERFACE = XopProperties.getProperty(XOPKEYS.BIND.INTERFACE);
    }

    interface SSL {
        String KEYSTORE = XopProperties.getProperty(XOPKEYS.SSL.KEYSTORE);
        String TRUSTSTORE = XopProperties.getProperty(XOPKEYS.SSL.TRUSTSTORE);
        String PASSWORD = XopProperties.getProperty(XOPKEYS.SSL.PASSWORD);
    }

    interface ENABLE {
        boolean GATEWAY = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.GATEWAY);
        boolean STREAM = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.STREAM);
        boolean LOGGING = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.LOGGING);
        boolean ANDROIDLOGGING = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.ANDROIDLOGGING);
        boolean DEVICELOGGING = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.DEVICELOGGING);
        boolean COMPRESSION = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.COMPRESSION);
        boolean DELAY = XopProperties.getBooleanProperty(XOPKEYS.ENABLE.DELAY);
    }

     interface STREAM {
        int PORT = XopProperties.getIntProperty(XOPKEYS.STREAM.PORT);
        String JID = XopProperties.getProperty(XOPKEYS.STREAM.JID);
    }

    interface TLS {
        boolean AUTH = XopProperties.getBooleanProperty(XOPKEYS.TLS.AUTH);
    }

    interface SDS {
        String SERVICE = XopProperties.getProperty(XOPKEYS.SDS.SERVICE);
        String INTERFACE = XopProperties.getProperty(XOPKEYS.SDS.INTERFACE);
        interface INDI {
            String CQI = XopProperties.getProperty(XOPKEYS.SDS.INDI.CQI);
            Boolean DUPLICATES = XopProperties.getBooleanProperty(XOPKEYS.SDS.INDI.ALLOW_DUPLICATES);
            Boolean LOOPBACK = XopProperties.getBooleanProperty(XOPKEYS.SDS.INDI.ALLOW_LOOPBACK);
            int NUMRA = XopProperties.getIntProperty(XOPKEYS.SDS.INDI.NUMRA);
            int NUMSCM = XopProperties.getIntProperty(XOPKEYS.SDS.INDI.NUMSCM);
            String ADDRESS = XopProperties.getProperty(XOPKEYS.SDS.INDI.ADDRESS);
            int PORT = XopProperties.getIntProperty(XOPKEYS.SDS.INDI.PORT);
            Long RAI = XopProperties.getLongProperty(XOPKEYS.SDS.INDI.RAI);
            Long SCI = XopProperties.getLongProperty(XOPKEYS.SDS.INDI.SCI);
            Long TTL = XopProperties.getLongProperty(XOPKEYS.SDS.INDI.TTL);
        }

        interface SIMPLE {
            String ADDRESS = XopProperties.getProperty(XOPKEYS.SDS.SIMPLE.ADDRESS);
            int PORT = XopProperties.getIntProperty(XOPKEYS.SDS.SIMPLE.PORT);
        }
    }

    interface TRANSPORT {
        String SERVICE = XopProperties.getProperty(XOPKEYS.TRANSPORT.SERVICE);
        String ADDRESS = XopProperties.getProperty(XOPKEYS.TRANSPORT.ADDRESS);
        String PORTRANGE = XopProperties.getProperty(XOPKEYS.TRANSPORT.PORTRANGE);
        long NODE_ID = XopProperties.getLongProperty(XOPKEYS.TRANSPORT.NODE_ID);
        String INTERFACE = XopProperties.getProperty(XOPKEYS.TRANSPORT.INTERFACE);
        int TTL = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.TTL);

        interface TE {
            int PORT = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.TE.PORT);
            String ADDRESS =
                    XopProperties.getProperty(XOPKEYS.TRANSPORT.TE.ADDRESS);
            int LISTENPORT = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.TE.LISTENPORT);
            String GROUPRANGE = XopProperties.getProperty(XOPKEYS.TRANSPORT.TE.GROUPRANGE);

            boolean RELIABLE =
                    XopProperties.getBooleanProperty(XOPKEYS.TRANSPORT.TE.RELIABLE);
            boolean ORDERED =
                    XopProperties.getBooleanProperty(XOPKEYS.TRANSPORT.TE.ORDERED);

            int PERSISTTIME =
                    XopProperties.getIntProperty(XOPKEYS.TRANSPORT.TE.PERSISTTIME);
            String CONFIGFILE = XopProperties.getProperty(XOPKEYS.TRANSPORT.TE.CONFIGFILE);
            String LOGLEVEL = XopProperties.getProperty(XOPKEYS.TRANSPORT.TE.LOGLEVEL);
        }
        // BR&T: Added Group Comm properties
        interface GC {
            long DISCCOVERYDELAY = XopProperties.getLongProperty(XOPKEYS.TRANSPORT.GC.DISCOVERYDELAY);

            interface AGENT {
                String ADDRESS = XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.AGENT.ADDRESS);
                int PORT = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.GC.AGENT.PORT);
            }

            interface DISCOVERY {
                int GROUP = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.GC.DISCOVERY.GROUP);
            }

            interface ROOMS {
                String BESTEFFORT =
                        XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.ROOMS.BESTEFFORT);
                String RELIABLE =
                        XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.ROOMS.RELIABLE);
                String ORDERED = XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.ROOMS.ORDERED);
                String AGREED = XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.ROOMS.AGREED);
                String SAFE = XopProperties.getProperty(XOPKEYS.TRANSPORT.GC.ROOMS.SAFE);
            }
        }

        // NORM properties
        interface NORM {
            long RCVBUFFERSPACE = XopProperties.getLongProperty(XOPKEYS.TRANSPORT.NORM.RCVBUFFERSPACE);
            long SENDBUFFERSPACE = XopProperties.getLongProperty(XOPKEYS.TRANSPORT.NORM.SENDBUFFERSPACE);
            int SEGMENTSIZE = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.NORM.SEGMENTSIZE);
            short BLOCKSIZE = XopProperties.getShortProperty(XOPKEYS.TRANSPORT.NORM.BLOCKSIZE);
            short NUMPARITY = XopProperties.getShortProperty(XOPKEYS.TRANSPORT.NORM.NUMPARITY);

            int GRTT_MULTIPLIER = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.NORM.GRTT_MULTIPLIER);

            interface SD {
                long INTERVAL = XopProperties.getLongProperty(XOPKEYS.TRANSPORT.NORM.SD.INTERVAL);
                int TIMEOUT = XopProperties.getIntProperty(XOPKEYS.TRANSPORT.NORM.SD.TIMEOUT);
            }
        }
    }

    interface GATEWAY {
        String SERVER = XopProperties.getProperty(XOPKEYS.GATEWAY.SERVER);
        String CONFERENCESERVER = XopProperties.getProperty(XOPKEYS.GATEWAY.CONFERENCESERVER);
        int PORT = XopProperties.getIntProperty(XOPKEYS.GATEWAY.PORT);
        int PING = XopProperties.getIntProperty(XOPKEYS.GATEWAY.PING);
        int TIMEOUT = XopProperties.getIntProperty(XOPKEYS.GATEWAY.TIMEOUT);
        int RECONNECT = XopProperties.getIntProperty(XOPKEYS.GATEWAY.RECONNECT);
        boolean TLSAUTH = XopProperties.getBooleanProperty(XOPKEYS.GATEWAY.TLSAUTH);
        String BINDINTERFACE = XopProperties.getProperty(XOPKEYS.GATEWAY.BINDINTERFACE);
        String KEYSTORE = XopProperties.getProperty(XOPKEYS.GATEWAY.KEYSTORE);
        String TRUSTSTORE = XopProperties.getProperty(XOPKEYS.GATEWAY.TRUSTSTORE);
        String STOREPASSWORD = XopProperties.getProperty(XOPKEYS.GATEWAY.STOREPASSWORD);
        String TRUSTSTOREPASSWORD = XopProperties.getProperty(XOPKEYS.GATEWAY.TRUSTSTOREPASSWORD);
        boolean ACCEPT_SELF_SIGNED_CERTS = XopProperties.getBooleanProperty(XOPKEYS.GATEWAY.ACCEPT_SELF_SIGNED_CERTS);
        String DOMAIN = XopProperties.getProperty(XOPKEYS.GATEWAY.DOMAIN) ;
        String CONFERENCEDOMAIN = XopProperties.getProperty(XOPKEYS.GATEWAY.CONFERENCEDOMAIN) ;

        boolean REWRITEDOMAIN = XopProperties.getBooleanProperty(XOPKEYS.GATEWAY.REWRITEDOMAIN);
    }
}