package org.fidonet.jftn;

import org.apache.log4j.Logger;
import org.fidonet.config.Config;
import org.fidonet.jftn.engine.script.JFtnShare;
import org.fidonet.jftn.engine.script.ScriptManager;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.protocol.binkp.BinkP;
import org.fidonet.protocol.binkp.SessFile;
import org.fidonet.protocol.binkp.SessionResult;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.io.RandomAccessFile;

public class JToss {

    private static Logger logger = Logger.getLogger(JToss.class);

    private static void Help() {
        System.out.println("java ftn usage:");
        System.out.println("jftn <action>:");
        System.out.println("    help - show this help");
        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) throws Exception {

        logger.debug("Starting JToss...");
        final long starttime = System.currentTimeMillis();
        Config config = new Config();
        config.ReadConfig("jftn.conf");
        if (!config.isValid()) {
            return;
        }

        // Loading Script engine
        ScriptManager scriptManager = new ScriptManager("scripts");

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
                logger.info("Executing "+commandString+" command...");
                command.execute(args);
            } else {
                logger.debug("Trying to OOTB command...");
                if (args[0].equalsIgnoreCase("toss")) {
                    Tosser tosser = new Tosser(config);
                    tosser.runFast(config.getInbound());
                } else if (args[0].equalsIgnoreCase("help")) {
                    Help();
                } else if (args[0].equalsIgnoreCase("poll")) {
                    FTNAddr boss = new FTNAddr("2:5030/1111.0");
                    BinkP bink = new BinkP();
                    SessionResult res = bink.Poll(config.getLink(boss), config);
                    SessFile[] files = res.getFiles();
                    for (int i = 0; i < res.getStatus(); i++) {
                        RandomAccessFile of = null;
                        try {
                            of = new RandomAccessFile(config.getInbound() + "/" + files[i].filename, "rw");
                            if (files[i].body != null) {
                                        of.write(files[i].body);
                            }
                            of.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            if (of != null) {
                                of.close();
                            }
                        }
                    }

                    System.out.println("Session " + res.getStatus());
                } else {
                    System.out.println("Error: Unknown commandline argument.");
                    Help();
                }
            }
        }

        logger.debug("Finish working (time: " + (System.currentTimeMillis() - starttime) / 1000.0 + " sec)");
    }
}
