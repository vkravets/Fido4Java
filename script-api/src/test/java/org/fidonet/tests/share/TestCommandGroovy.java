/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.tests.share;

import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.ScriptApi;
import org.fidonet.jftn.engine.script.ScriptEngine;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandRegistry;
import org.fidonet.jftn.share.ScriptHookRegistry;
import org.fidonet.jftn.tools.FileUtils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;

/**
 * Created by IntelliJ IDEA.
 * User: Vladimir Kravets
 * Date: 8/30/11
 * Time: 5:46 PM
 */
public class TestCommandGroovy {

    @Test
    public void testCommandRegister() throws Exception {
        // Init JythonScriptManager
        ScriptEngine scriptManager = new GroovyScriptManager();
        // Init hook and command classes
        ScriptHookRegistry hookInterpreter = new ScriptHookRegistry();
        CommandCollection commands = new CommandCollection();
        CommandRegistry commandInterpreter = new CommandRegistry(commands);
        // Add to script scope "jftn" variable which have all above data
        String fileName = "testCommand.groovy";
        try (InputStream inputStream = ScriptEngine.class.getClassLoader()
                                                         .getResourceAsStream(fileName)) {
            String fileContent = FileUtils.getFileContent(inputStream);
            scriptManager.registerScript(fileName,
                                         fileContent,
                                         new ScriptApi(scriptManager,
                                                       hookInterpreter,
                                                       commandInterpreter));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //noinspection unchecked
        Command<String[], Object> command = commands.findCommandByName("test");
        Assertions.assertNotNull(command);
        String[] param = new String[]{"test", "test2"};
        Object result = command.execute(param);
        Assertions.assertEquals(param, result);
    }
}
