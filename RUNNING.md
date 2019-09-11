# Run
To run, in the xop/dist directory:

    ./start_xop.sh [configuration options]

# Configuring XOP
After building XO, there will be a default configuration file, config/xop.properties that 
XO will load upon startup. To override:

> ./start_xop.sh -Dproperties.file=path_to_properties

Use commandline properties starting with '-D' to override properties in the
config/xop.properties file. e.g.

> ./start_xop.sh -Dxop.transport.nodeid=100

''Specifying command-line system properties WILL take precedence over the
`config/xop.properties` file.''

### Create configuration file
If you would like to create a xop.properties file, there is a helper script to generate default properties:

    java -cp xop.jar edu.drexel.xop.util.XopProperties > dist/config/xop.properties
    
### Common commandline parameters

  -Dxop.enable.gateway=true|false

  -Dxop.transport.nodeid=<integer> 

  -Djava.util.logging.config.file=<path to logging.properties>

## Logging
Logging can be configured by modifying the `config/logging.properties` file
prior to building and execution.

Demos and experiments should set all logging levels to INFO. Further
instructions for using the `config/logging.properties` file is specified in the
file.

You can also specify your own logging.properties at the command line:

    ./run.sh -Djava.util.logging.config.file=config/logging.properties

or:

    java -Djava.util.logging.config.file=config/logging.properties -jar xop.jar

## Federating with Openfire or other XMPP server
A XOP instance within a MANET can use an external interface to maintain a persistent
connection to an enterprise XMPP server, e.g. Openfire. This XOP instance will 
forward XMPP Presence, IQ, and Messages to/from a standard XMPP server.

### Configuring XOP for Gatewaying

- Import Openfire server certificates into the keystore used by XOP 
- edit the following properties:
  - xop.enable.gateway=true
  - xop.gateway.server=<servername>

- Add DNS entries or modify the /etc/hosts files with hostnames or 

- Ensure the servername maps correctly to the openfire server and ip address of the server
  - e.g. include in /etc/hosts: <openfire IP address> <openfire server hostname>
- ensure the gatewayed XOP instance has the hostname of the proxied 
  - e.g. include in /etc/hosts: <external IP address of XOP node> proxy

### Configuring Openfire

- Enable the following properties:
  - Dialback protocol (XEP-220)
  - server self-signed certificates
  - server tls enabled
 
