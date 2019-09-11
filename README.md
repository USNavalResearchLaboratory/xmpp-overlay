# XMPP Overlay Service
* Version
  * xop 0.10.0 CWIX 2019
  * xop 0.9.5 CWIX 2018
  * xop 0.9.4xAB17 latest version with support for AgileBloodhound 2017
  * xop 0.99 is based on subversion rev 2598 (See CHANGELOG.txt for more information)
  * xop 0.9 is based on subversion rev 2355 (See CHANGELOG.txt for more information)
  * xop 0.8.XX based on subversion revision r2252 
* Author: Duc Nguyen <dnguyen580@gmail.com>

# About
The XMPP Overlay Proxy (XOP) is a solution for running XMPP Multi-User Chat
(MUC) applications in distributed, heterogeneous, serverless environments. Using
the transport engine (also developed as part of the Groupwise project), XOP
allows for for serverless, asynchronous, group-oriented messaging and service
discovery using a variety of standard protocols.

XOP can be used with familiar commercial clients (Pidgin, Xabber, iChat, etc.)
and gateways to standard enterprise XMPP server infrastructures (e.g, Openfire).

# Quick Start

## Start XO in Linux

In the XOP trunk, edit settings.gradle (sample in settings.gradle.sample) to point 
to proper path for transport engine.

Run:

```bash
./gradlew deployXOP
cd dist
./start_xop.sh
```

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

## Build Dependencies and Prerequisites
XO depends on the following being installed and configured properly:

*  Java 1.8
*  Transport Engine (optional)
*  NRL SMF (if you want multi-hop communications)

# Using XOP
XO runs on Android devices and on computers running Linux and Mac.

## Android

As of 2018-02-07, the Android version of XOP requires GCSD from Boeing. This
isn't included in the distribution. Soon, XO will be tested with
'norm-transport'

### Prerequisites
* OpenJDK 8
* Android SDK 23 

### Building an Android xop.apk
Android device must allow installing applications outside of Google Play App Store.

To build:
   
```bash
cd [xop root]
./gradlew assembleDebug 
```

### Run
* Open XOP app
* Select "Settings" under the "Menu"
* Tap the "ON" button. Once XOP is finished initializing, the button will change 
  text to say "ON"

## Linux/Mac

### Prerequisites
* Java Development Kit (JDK) 8 (open JDK is ok)
* Android SDK 18 

### Building
XOP uses the gradle build system now to build for all platforms

To build XOP for linux and windows, run in the same directory as this README:

```bash
./gradlew deployXOP
```

### Run
To run, in the xop/dist directory:

```bash
./start_xop.sh [configuration options]
```

## Windows

### Run
To run, in the xop/dist directory:

```bash
./list_nets.sh
## Make note of the interface name for the active interface
start_xop.bat -Dxop.bind.interface={interface}
```

# Configuring XOP
After building XO, there will be a default configuration file, config/xop.properties that 
XO will load upon startup. To override:

```bash
./start_xop.sh -Dproperties.file=path_to_properties
```

Use commandline properties starting with '-D' to override properties in the
config/xop.properties file. e.g.
```bash
./start_xop.sh -Dxop.transport.nodeid=100
```

**_Specifying command-line system properties WILL take precedence over the
`config/xop.properties` file._**

### Create configuration file
If you would like to create a xop.properties file, there is a helper script to generate default properties:

```bash
java -cp xop.jar edu.drexel.xop.util.XopProperties > dist/config/xop.properties
```    
### Common commandline parameters

```bash
./start_xop.sh -Dxop.enable.gateway=true|false \
  -Djava.util.logging.config.file={path to logging.properties}
```

## Logging
Logging can be configured by modifying the `config/logging.properties` file
prior to building and execution.

Demos and experiments should set all logging levels to INFO. Further
instructions for using the `config/logging.properties` file is specified in the
file.

You can also specify your own logging.properties at the command line:

```bash
./start_xop.sh -Djava.util.logging.config.file=config/logging.properties
```
or:
```bash
java -Djava.util.logging.config.file=config/logging.properties -jar xop-all.jar
```

## Enabling gatewaying to another enterprise server:
A XOP instance within a MANET can use an external interface to maintain a persistent
connection to an enterprise XMPP server, e.g. Openfire. This XOP instance will 
forward XMPP Presence, IQ, and Messages to/from a standard XMPP server.

### Configuring XOP for Gatewaying

- Import Openfire server certificates into the keystore used by XOP 
- edit the following properties:
  ```bash
  ./start_xop.sh -Dxop.enable.gateway=true -Dxop.gateway.server={servername}
  ```

- Ensure the servername maps correctly to the openfire server and ip address of the server
  - e.g. include in `/etc/hosts: <openfire IP address> <openfire server hostname>`
- ensure the gatewayed XOP instance has the hostname of the proxied 
  - e.g. include in `/etc/hosts: <external IP address of XOP node> <XO domain>`

### Configuring Openfire

- Enable the following properties:
  - Dialback protocol (XEP-220)
  - server self-signed certificates
  - server tls enabled
  
## NORM
The `lib` directory contains all jar dependencies to run XOP.  However, the
included NORM library and the jni bindings are linux specific, and may not work
on your machine, since these are platform dependent.  To get NORM working on
your machine, you may need to obtain the NORM source and build NORM with the
Java JNI bindings and place the `libnorm.so` and `norm.jar` in jniLibs/`, and
rebuild XOP.
After building (i.e. running `./gradlew`), the `jniLibs/` directory (and its
contents) are copied into the `dist/` directory.

# Glossary
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


