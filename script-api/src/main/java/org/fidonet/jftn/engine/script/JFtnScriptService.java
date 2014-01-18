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

package org.fidonet.jftn.engine.script;

import org.fidonet.config.IConfig;
import org.fidonet.events.AbstractEventHandler;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/31/11
 * Time: 10:29 AM
 */
public class JFtnScriptService {

    private CommandInterpreter commands;
    private HookInterpreter hooks;
    private IConfig config;

    public JFtnScriptService(ScriptEngine scriptEngine, HookInterpreter hooks, CommandInterpreter commands) {
        this.hooks = hooks;
        this.commands = commands;
    }

    public void registerCommand(String name, Command command) throws Exception {
        commands.registerCommand(name, command);
    }

    public void registerHook(AbstractEventHandler hook) throws Exception {
        hooks.registerHook(hook);
    }

    public void unregisterHook(AbstractEventHandler hook) throws Exception {
        hooks.unregisterHook(hook);
    }

    public IConfig getConfig() {
        return config;
    }

    public void setConfig(IConfig config) {
        this.config = config;
    }

}