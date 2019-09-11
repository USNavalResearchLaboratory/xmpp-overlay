package edu.drexel.transportengine.core;

import com.martiansoftware.jsap.FlaggedOption;
import com.martiansoftware.jsap.JSAP;
import com.martiansoftware.jsap.JSAPException;
import com.martiansoftware.jsap.JSAPResult;
import edu.drexel.transportengine.util.config.Configuration;

import java.io.IOException;
import java.util.Random;

/**
 * Main entry point for the Transport Engine.
 *
 * @author Aaron Rosenfeld <ar374@drexel.edu>
 */
public class Run {

    /**
     * Main function to launch the Transport Engine.
     *
     * @param args command line arguments.
     * @throws IOException if the configuration file can not be read.
     */
    public static void main(String[] args) throws IOException, JSAPException {

        JSAP jsap = new JSAP();
        jsap.registerParameter(new FlaggedOption("id")
                .setLongFlag("id")
                .setStringParser(JSAP.STRING_PARSER)
                .setDefault(String.valueOf(new Random().nextInt(1000)))
                .setRequired(true)
                .setHelp("Sets the GUID of the TE."));
        jsap.registerParameter(new FlaggedOption("config")
                .setLongFlag("config")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(false)
                .setHelp("Specifies a configuration file to use for the TE."));
        jsap.registerParameter(new FlaggedOption("generate-config-at")
                .setLongFlag("generate-config-at")
                .setStringParser(JSAP.STRING_PARSER)
                .setRequired(false)
                .setHelp("Generates a configuration file for the TE at the specified path.  This does *not* run the " +
                        "TE process."));

        JSAPResult flags = jsap.parse(args);

        if (!flags.success()) {
            System.err.println("Usage: java " + Run.class.getName());
            System.err.println("    " + jsap.getUsage());
            System.exit(1);
        }

        if (flags.contains("generate-config-at")) {
            Configuration.getInstance().wrtieToFile(flags.getString("generate-config-at"));
        } else {
            if (flags.contains("config")) {
                Configuration.getInstance().loadFile(flags.getString("config"));
            }
            TransportEngine engine = new TransportEngine(flags.getString("id"));
            engine.start();
        }
    }
}
