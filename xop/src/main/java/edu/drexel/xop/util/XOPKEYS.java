package edu.drexel.xop.util;

/**
* ${FILE}
*
* @author duc
*         Created: 8/7/14
*         Description: keys for the properties loaded from the properties files.
*/
public interface XOPKEYS {
    String DOMAIN = "xop.domain";
    String CONFERENCE_SUBDOMAIN = "xop.conference.subdomain";
    String PORT = "xop.port";
    String MUCPORT = "xop.muc.port";

    interface BIND {
        String INTERFACE = "xop.bind.interface";
    }

    interface ENABLE {
        String GATEWAY = "xop.enable.gateway";
        String STREAM = "xop.enable.stream";
        String LOGGING = "xop.enable.logging";
        String ANDROIDLOGGING = "xop.enable.app.logging";
        String DEVICELOGGING = "xop.enable.file.logging";
        String COMPRESSION = "xop.enable.compression";
        String DELAY = "xop.enable.delay";
    }

    // interface ONETOONE {
    //     String LISTENPORT = "xop.onetoone.port";
    //     String ADDRESS = "xop.onetoone.address";
    // }

    interface TLS {
        String AUTH = "xop.tls.auth";
    }

    interface SSL {
        String KEYSTORE = "xop.ssl.keystore";
        String TRUSTSTORE = "xop.ssl.truststore";
        String PASSWORD = "xop.ssl.password";
    }

    interface SDS {
        // BR&T: Allow selection of discovery service (protosd vs groupcomms)
        String SERVICE = "xop.sds.service";
        String INTERFACE = "xop.sds.interface";
        interface INDI  {
            String ADDRESS = "xop.sds.indi.address";
            String PORT = "xop.sds.indi.port";
            String TTL = "xop.sds.indi.ttl";
            String RAI = "xop.sds.indi.rai";
            String NUMRA = "xop.sds.indi.numra";
            String CQI = "xop.sds.indi.cqi";
            String SCI = "xop.sds.indi.sci";
            String NUMSCM = "xop.sds.indi.numscm";
            String ALLOW_LOOPBACK = "xop.sds.indi.allow.loopback";
            String ALLOW_DUPLICATES = "xop.sds.indi.allow.duplicates";
        }
        String ENABLE_REMOVE_PRESENCE = "xop.sds.remove.presence";

        interface SIMPLE {
            String ADDRESS = "xop.sds.simple.address";
            String PORT = "xop.sds.simple.port";
        }
    }

    interface STREAM {
        String PORT = "xop.stream.port";
        String JID = "xop.stream.jid";
    }

    interface TRANSPORT {
        String SERVICE = "xop.transport.service";
        String ADDRESS = "xop.transport.address";
        String PORTRANGE = "xop.transport.portrange";
        String NODE_ID = "xop.transport.nodeid";
        String INTERFACE = "xop.transport.interface";
        String TTL = "xop.transport.ttl";
        interface TE {
            String ADDRESS = "xop.transport.te.address";
            String GROUPRANGE = "xop.transport.grouprange";
            String LISTENPORT = "xop.transport.te.listenport";
            String PORT = "xop.transport.te.port";
            String RELIABLE = "xop.transport.te.reliable";
            String ORDERED = "xop.transport.te.ordered";
            String PERSISTTIME = "xop.transport.te.persist.time";
            String  CONFIGFILE = "xop.transport.te.configfile";
            String LOGLEVEL = "xop.transport.te.loglevel";
        }
        // BR&T: GC discovery parameters
        interface GC  {
            String DISCOVERYDELAY = "xop.gc.discovery.delay";

            interface AGENT {
                String ADDRESS = "xop.gc.agent.address";
                String PORT = "xop.gc.agent.port";
            }
            interface DISCOVERY {
                String GROUP = "xop.gc.discovery.group";
            }

            interface ROOMS {
                String BESTEFFORT = "xop.transport.gc.rooms.besteffort";
                String RELIABLE = "xop.transport.gc.rooms.reliable";
                String ORDERED = "xop.transport.gc.rooms.ordered";
                String AGREED = "xop.transport.gc.rooms.agreed";
                String SAFE = "xop.transport.gc.rooms.safe";
            }
        }

        interface NORM {
            String RCVBUFFERSPACE = "xop.transport.norm.rcvbufferspace";
            String SENDBUFFERSPACE = "xop.transport.norm.sendbufferspace";
            String SEGMENTSIZE = "xop.transport.norm.segmentsize";
            String BLOCKSIZE = "xop.transport.norm.blocksize";
            String NUMPARITY = "xop.transport.norm.numparity";
            String GRTT_MULTIPLIER = "xop.transport.norm.grttmultiplier";

            interface SD {
                String INTERVAL = "xop.transport.norm.sd.interval";
                String TIMEOUT = "xop.transport.norm.sd.timeout";
            }
        }
    }

    interface GATEWAY {
        String BINDINTERFACE = "xop.gateway.bindinterface";
        String SERVER = "xop.gateway.server";
        String CONFERENCESERVER = "xop.gateway.conference.server";
        String PORT = "xop.gateway.port";
        String PING = "xop.gateway.ping";
        String TIMEOUT = "xop.gateway.timeout";
        String RECONNECT = "xop.gateway.reconnect";
        String TLSAUTH = "xop.gateway.tlsauth";

        String KEYSTORE = "xop.gateway.keystore";
        String TRUSTSTORE = "xop.gateway.truststore";
        String STOREPASSWORD = "xop.gateway.keystore.password";
        String TRUSTSTOREPASSWORD = "xop.gateway.truststore.password";
        String ACCEPT_SELF_SIGNED_CERTS = "xop.gateway.accept.selfsigned.certs";
        String DOMAIN = "xop.gateway.domain";
        String CONFERENCEDOMAIN = "xop.gateway.conference.domain";

        String REWRITEDOMAIN = "xop.gateway.rewritedomain";
    }
}
