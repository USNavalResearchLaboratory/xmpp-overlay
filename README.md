# About
The XMPP Overlay Proxy (XOP) is a solution for running XMPP Multi-User Chat
(MUC) applications in distributed, heterogeneous, serverless environments. Using
the transport engine (also developed as part of the Groupwise project), XOP
allows for for serverless, asynchronous, group-oriented messaging and service
discovery using a variety of standard protocols.

XOP can be used with familiar commercial clients (Pidgin, Spark, iChat, etc.)
and gateways to standard enterprise XMPP server infrastructures (e.g, Openfire).

# Quick Start
## Start XO
Check out the Transport Engine and XOP trunks.  Set the `TRANSPORT_ENGINE_PATH`
environment variable to the location of the Transport Engine trunk.

Then, in the XOP trunk, run:

    ant
    ./run.sh

If this didn't produce any errors, XO should be running on the loopback
interface on port 5222 (the default XMPP server port).

## Connect a client to XO
You can now connect your favorite XMPP client to XO.  Simply enter the
following information (field names taken from Pidgin):

* Username:  [whatever]
* Domain:  127.0.0.1
* Resource:  [whatever, or leave it blank]
* Password:  [whatever]
* Connection Security:  [any of the choices should work]
* Connect port:  5222
* Connect Server:  127.0.0.1

## Join a chatroom
Connect (in Pidgin, "enable" the account) your client to the server.  Then go
to "Join a Chat".  Enter the following info:

* Room:  [whatever]
* Server:  conference.proxy
* Handle: [whatever, probably best if it matches your username above]
* Password:  [leave it blank]

# Dependencies
XO depends on the following being installed and configured properly:

*  java 1.7 or higher
*  transport-engine v0.2
*  NRL SMF (if you want multi-hop communications)
*  NRL NORM (if you want to use the NORM protocol)

# Using XOP
## Building
To build XOP, run in the same directory as this README:

    ant

## Running
To run, in the same directory as this README:

    ./run.sh

This uses a customized run script. Alternatively, you can run:

    java -jar dist/xop.jar

to run the XOP jar by itself.

## Cleaning
To clean (after a previous build):

    ant clean

# Configuring XOP
By default, there is no configuration file.  If you would like to change any
of the default configuration settings, do one of the following:

## Configure with a Configuration File (removed whenever you do "ant clean")
*  Run "dist/generate_config.sh > ../config/xop.properties"
*  Edit config/xop.properties to make the desired changes.

## Configure with the Command-Line Parameters
* Run run.sh as normal, but use "-Dparameter=value" to change the desired
  settings.  For example, "./run.sh -D xop.bind.interface=eth0".

Specifying command-line system properties WILL take precedence over the
`config/xop.properties` file.

## Components
Components are deployed into the `plugins/` directory. The components are
enabled by the `xop.enabled.components` property in `config/xop.properties`.

### "muc" Component
This component implements XEP-0045, "Multi-User Chat" and it's respective
service discovery mechanisms. This is now a part of the core of XOP. NO need to include it as a component in xop.enabled.components properties.

### "s2s" Component
This component implements XEP-0220, "Server Dialback" to enable server-to-server
federation. Must be used in tandem with the "xog" component

### "xog" Component
This component gateways messages from a local XOP Instance to a federated
server. Must be used in tandem with the "s2s" component.

### "ad" Component
This component sends mDNS advertisements (using protosd), advertising itself as
an XMPP server, as specified in RFC 6120.  It also advertises itself as an
"xop-server".

## Logging
Logging can be configured by modifying the `config/logging.properties` file
prior to building and execution.

Demos and experiments should set all logging levels to INFO. Further
instructions for using the `config/logging.properties` file is specified in the
file.

You can also specify your own logging.properties at the command line:

    ./run.sh -D java.util.logging.config.file=config/logging.properties

or:

    java -Djava.util.logging.config.file=config/logging.properties -jar xop.jar

## NORM
The `lib` directory contains all jar dependencies to run XOP.  However, the
included NORM library and the jni bindings are linux specific, and may not work
on your machine, since these are platform dependent.  To get NORM working on
your machine, you may need to obtain the NORM source and build NORM with the
Java JNI bindings and place the `libnorm.so` and `norm-jni.jar` in `lib/`, and
rebuild XOP.

After building (i.e. running `ant`), the `lib/` directory (and its
contents) are copied into the `dist/` directory.

## Glossary
* Client - An XMPP client, e.g. Pidgin, `listen.py`
* Configuration File - `xop.properties`
* Local Client - a Client connected to a local XOP Instance (on the same node)
* Logging Config - `logging.properties`
* MUC - Multi-User Chat
* Node - A host machine, presumably running a single XOP Instance with one or
  more Clients connecting to it.
* Remote Client - a client on a remote node connected to the XOP
  instance executing on that node.
* XOG - the XO Gateway, connects serverless XO "clouds" with enterprise XMPP
  servers such as OpenFire
* XOP Instance - a running XOP process, i.e. running `java -jar xop.jar`

## Details
### Version
* xop_0.8.1 based on subversion revision r2272 (See CHANGELOG.txt for more information)

### Last Updated
* 2013-06-17

### Authors
* Dustin Ingram <dustin@cs.drexel.edu>
* Rob Lass <urlass@cs.drexel.edu>
* Duc Nguyen <dn53@drexel.edu>
