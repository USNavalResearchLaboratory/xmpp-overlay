package edu.drexel.xop.util;

/**
 * Constants class. Makes references to XOP.java interface and classes.
 * 
 */
public interface CONSTANTS {
    interface AUTH {
        String STREAM_FEATURES_TLS = ("<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'><required/></starttls></stream:features>");
        String STREAM_FEATURES_PLAIN = "<stream:features><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>";
        String STREAM_FEATURES_TLS_OPTIONAL = "<stream:features><starttls xmlns='urn:ietf:params:xml:ns:xmpp-tls'><optional/></starttls><mechanisms xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><mechanism>PLAIN</mechanism></mechanisms></stream:features>";
        String STREAM_CLOSE = "</stream:stream>";
        String STARTTLS_PROCEED = "<proceed xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
        String STARTTLS_FAIL = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-tls'/>";
        String TEMPORARY_FAIL = "<failure xmlns='urn:ietf:params:xml:ns:xmpp-sasl'><temporary-auth-failure/></failure>";
        String SUCCESS = "<success xmlns='urn:ietf:params:xml:ns:xmpp-sasl'/>";
        String BIND_FEATURE = "<bind xmlns='urn:ietf:params:xml:ns:xmpp-bind'/>";
        String SESSION_FEATURE = "<session xmlns='urn:ietf:params:xml:ns:xmpp-session'/>";
        String SUB_FEATURE = "<sub xmlns='urn:xmpp:features:pre-approval'/>";
        String STREAM_FEATURES = "<stream:features>" + BIND_FEATURE + SESSION_FEATURE + SUB_FEATURE
                + "</stream:features>";
    }
    interface DISCO {
        String ITEM_NAMESPACE = "http://jabber.org/protocol/disco#items";
        String INFO_NAMESPACE = "http://jabber.org/protocol/disco#info";
        String MUC_NAMESPACE = "http://jabber.org/protocol/muc";
        String MUC_USER_NAMESPACE = "http://jabber.org/protocol/muc#user";
        String BYTESTREAM_NAMESPACE = "http://jabber.org/protocol/bytestreams";
        String ROSTER_NAMESPACE = "jabber:iq:roster";
        String SUBS_PREAPPROVAL = "urn:xmpp:features:pre-approval";
        String SERVER_NAMESPACE = "jabber:server";
        String AUDIO_NAMESPACE = "urn:xmpp:jingle:apps:rtp:audio";
        String JINGLE_NAMESPACE = "urn:xmpp:jingle:1";
        String XML_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-stanzas";
        String CHATSTATES_NAMESPACE = "http://jabber.org/protocol/chatstates";
    }

    interface GATEWAY {
        String SERVER_NAMESPACE = "jabber:server";
        String SERVER_DIALBACK_NAMESPACE = "jabber:server:dialback";
        String SERVER_DIALBACK_FEATURE_NS = "urn:xmpp:features:dialback";
        String SERVER_DIALBACK_FEATURE_ELEMENT = "<dialback xmlns='" + SERVER_DIALBACK_FEATURE_NS + "'/>";
        String SERVER_DIALBACK_FEATURE_ELEMENT_ERRORS =
                "<dialback xmlns='" + SERVER_DIALBACK_FEATURE_NS + "'><errors/></dialback>";
        String TLS_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-tls";
        String TLS_FEATURE_ELEMENT = "<starttls xmlns='"+TLS_NAMESPACE+"'/>";
        String TLS_FEATURE_REQUIRED_ELEMENT = "<starttls xmlns='"+TLS_NAMESPACE+"'><required/></starttls>";
        String SASL_NAMESPACE = "urn:ietf:params:xml:ns:xmpp-sasl";
        // String SASL_FEATURE_ELEMENT = "<sasl xmlns='"+SASL_NAMESPACE+"'/>";
        String STREAM_ID = "s2s_stream"; //TODO dnn 2016-06-03 have random generated id.

        // dnn 2018-05-31 not used anywhere
        // String STREAM_OPEN_SERVER_WITH_DIALBACK =
        //         "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' "
        //                 + "xmlns='" + SERVER_NAMESPACE + "' "
        //                 + "xmlns:db='" + SERVER_DIALBACK_NAMESPACE + "' "
        //                 + "from='" + XOP.DOMAIN + "' to='" + XOP.GATEWAY.SERVER +"' "
        //                 + "id='"+ STREAM_ID  + "' "
        //                 + "xml:lang='en' version='1.0'>";
//                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' xmlns='"
//                        + SERVER_NAMESPACE + "' xmlns:db='" + SERVER_DIALBACK_NAMESPACE
//                        + "' from='" + XOP.DOMAIN + "' to='" + XOP.GATEWAY.SERVER
//                        + "' id='"+ STREAM_ID
//                        + "' xml:lang='en' version='1.0'>";
        String STREAM_OPEN_SERVER =
                "<stream:stream xmlns:stream='http://etherx.jabber.org/streams' "
                        + "xmlns='" + SERVER_NAMESPACE + "' "
                        //"xmlns:db='" + SERVER_DIALBACK_NAMESPACE + "' "
                        + "from='" + XOP.DOMAIN + "' to='" + XOP.GATEWAY.SERVER +"' "
                        + "id='"+ STREAM_ID  + "' "
                        + "xml:lang='en' version='1.0'>";

        String STREAM_FEATURES_DIALBACK_TLS =
                "<stream:features xmlns='http://etherx.jabber.org/streams'>"
                        // + SERVER_DIALBACK_FEATURE_ELEMENT
                        + SERVER_DIALBACK_FEATURE_ELEMENT_ERRORS
                        + TLS_FEATURE_ELEMENT
                        + "</stream:features>";
        String STREAM_FEATURES_DIALBACK =
                "<stream:features xmlns='http://etherx.jabber.org/streams'>"
                        // + SERVER_DIALBACK_FEATURE_ELEMENT
                        + SERVER_DIALBACK_FEATURE_ELEMENT_ERRORS
                        + "</stream:features>";
        String STREAM_FEATURES_EMPTY =
                "<features xmlns='http://etherx.jabber.org/streams'/>";
        String STARTTLS_REQUEST = "<starttls xmlns='" + TLS_NAMESPACE + "'/>";
        String PING_NAMESPACE = "urn:xmpp:ping";
    }

    interface PROTOSD {
        String JID_NODE = "_jid_node";
        String JID_DOMAIN = "_jid_domain";
        String JID_RESOURCE = "_jid_resource";
        String ALIAS = "_alias";
        String ADDRESS = "_address";
        String PRESENCE = "_presence";
        String STATUS = "_statusmsg";
        String ROOM = "_room";
        String SERVICE_ADDR = "_service_addr"; //"_service_addr" in newer versions of protoSD
        String PRODUCER = "_producer";
    }

    interface MISC {
        String DELAY_NAMESPACE = "urn:xmpp:delay";
        String XMPP_DATETIME_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";
    }
}