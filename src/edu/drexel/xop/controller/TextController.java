/**
 * (c) 2011 Drexel University
 *
 * Text-based controller for XOP.
 *
 * @author Rob Lass<urlass@cs.drexel.edu>
 *
 */
package edu.drexel.xop.controller;

import edu.drexel.xop.util.logger.LogUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Level;

public class TextController extends Controller {

    public TextController() {
        super(LogUtils.getLogger(TextController.class.getName()));
    }

    public void run() {
        System.out.println("Type 'exit' to quit.");
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                System.in));
        while (true) {
            String s;
            try {
                s = reader.readLine();
                if (s == null) {
                    System.err.println(
                            "STDIN is closed or set to null. Use Ctrl-C to close XOP");
                    return;
                }
            } catch (IOException e) {
                logger.log(Level.WARNING,
                        "IOException while waiting for command input");
                return;
            }
            sendRawCommand(s);
            if (command_is_ready.get() && (command.equals(Command.EXIT))) {
                System.err.println("exiting...");
                return;
            }
        }
    }
}
