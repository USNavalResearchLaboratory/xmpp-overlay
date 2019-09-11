# About
The Transport Engine (TE) is a generic group-based communication middleware,
providing an abstraction of transport layer details from the applications.
Instead of specifying a transport layer, applications utilize a JSON-based API
to specify the reliability, persistence, and ordering requirements for messages
being sent.

# Quick Start
To compile the Transport Engine and run it as a daemon:

    ant
    ./te-daemon.sh start

The log for this will be located at `/tmp/te_X.log` where X is an ID assigned to
the Transport Engine instance.  The full list of options can be found with
`./te-daemon --help` and are:

    * `start`: Starts the Transport Engine as a daemon.
    * `stop <id>`: Stops the Transport Engine with ID `id`.
    * `status`: Lists running Transport Engine instances and their associated
      PID.
    * `generate_config <path>`: Generates a configuration file as described
      below.
    * `help`: Will print a usage message.

# Custom Configuration
The Transport Engine can be run with a specified ID and/or configuration with:

    ./te-daemon.sh start --id <id> --config <config>

These are described below.

## Configuration Generation
By default, no configuration file is needed by the Transport Engine.  To
generate a configuration file, run

    ./te-daemon.sh generate-config <path>

This will create a configuration file with all possible configuration
parameters.  To run the Transport Engine with this configuration run:

    ./te-daemon.sh start --config <path>

## Custom GUID
When run, the Transport Engine generates a random 16-bit unsigned integer for
its globally unique identifier (GUID).  To instead specify a custom GUID, use:

    ./te-daemon.sh start --id <id>

The specified value need not be an integer, and can be an arbitrary string.
