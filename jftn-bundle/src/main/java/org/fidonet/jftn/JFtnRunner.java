/******************************************************************************
 * Copyright (c) 2012-2014, Vladimir Kravets                                  *
 *  All rights reserved.                                                      *
 *                                                                            *
 *  Redistribution and use in source and binary forms, with or without        *
 *  modification, are permitted provided that the following conditions are    *
 *  met: Redistributions of source code must retain the above copyright notice,
 *  this list of conditions and the following disclaimer.                     *
 *  Redistributions in binary form must reproduce the above copyright notice, *
 *  this list of conditions and the following disclaimer in the documentation *
 *  and/or other materials provided with the distribution.                    *
 *  Neither the name of the Fido4Java nor the names of its contributors       *
 *  may be used to endorse or promote products derived from this software     *
 *  without specific prior written permission.                                *
 *                                                                            *
 *  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,     *
 *  THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR    *
 *  PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR         *
 *  CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,     *
 *  EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,       *
 *  PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;
 *  OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,  *
 *  WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR   *
 *  OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,            *
 *  EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                        *
 ******************************************************************************/

package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.engine.script.JythonScriptManager;
import org.fidonet.jftn.engine.script.ScriptEngine;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

public class JFtnRunner {

    private static final ILogger logger = LoggerFactory.getLogger(JFtnRunner.class.getName());

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
        JFtnScriptService shareObject = new JFtnScriptService(scriptManager, hooks, commandInterpreter);
        shareObject.setConfig(config);
        // Loading all scripts
        scriptManager.reloadScripts(shareObject);

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
//                    Tosser tosser = new Tosser(config);
//                    tosser.runFast(config.getValue("inbound"));
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
