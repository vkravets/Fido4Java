package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.JythonScriptManager;
import org.fidonet.jftn.engine.script.ScriptEngine;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

public class JToss {

    private static ILogger logger = LoggerFactory.getLogger(JToss.class.getName());

    private static void Help() {
        System.out.println("java ftn usage:");
        System.out.println("jftn <action>:");
        System.out.println("    help - show this help");
        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) throws Exception {

        logger.debug("Starting JToss...");
        final long starttime = System.currentTimeMillis();
        JFtnConfig config = new JFtnConfig();
        try {
            config.load("jftn.conf");
        } catch (ParseConfigException e) {
            logger.error("Error during parsing config.", e);
            System.exit(1);
        }

        // Loading Script engine
        ScriptEngine scriptManager = new JythonScriptManager("scripts");

        HookInterpreter hooks = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        JFtnShare shareObject = new JFtnShare(scriptManager, hooks, commandInterpreter);
        shareObject.setConfig(config);
        scriptManager.addScriptVar("jftn", shareObject);
        // Loading all scripts
        scriptManager.reloadScripts();

        if (args.length == 0) {
            System.out.println("Error: No action in commandline.");
            Help();
        } else {
            String commandString = args[0];
            Command command = commands.findCommandByName(commandString);
            if (command != null) {
                logger.info("Executing " + commandString + " command...");
                command.execute(args);
            } else {
                logger.debug("Trying to OOTB command...");
                if (args[0].equalsIgnoreCase("toss")) {
                    Tosser tosser = new Tosser(config);
                    tosser.runFast(config.getValue("inbound"));
                } else if (args[0].equalsIgnoreCase("help")) {
                    Help();
                } else {
                    System.out.println("Error: Unknown commandline argument.");
                    Help();
                }
            }
        }

        logger.debug("Finish working (time: " + (System.currentTimeMillis() - starttime) / 1000.0 + " sec)");
    }
}
