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

import groovy.lang.GroovyClassLoader;
import junit.framework.TestCase;
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl;
import org.fidonet.jftn.engine.script.GroovyScriptManager;
import org.fidonet.jftn.engine.script.JFtnScriptService;
import org.fidonet.jftn.engine.script.ScriptEngine;
import org.fidonet.jftn.share.Command;
import org.fidonet.jftn.share.CommandCollection;
import org.fidonet.jftn.share.CommandInterpreter;
import org.fidonet.jftn.share.HookInterpreter;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 2/4/14
 * Time: 11:54 AM
 */
public class TestReloadScript {

    public static final String script1 = "import org.fidonet.jftn.share.Command\n" +
            "\n" +
            "class TossCommand implements Command<String[], String[]> {\n" +
            "    public static final String test_static = \"test\"\n" +

            "    @Override\n" +
            "    String[] execute(String[] argv) {\n" +
            "        return [test_static]\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "\n" +
            "def register(serviceApi) {\n" +
            "    serviceApi.registerCommand(\"test\", new TossCommand())\n" +
            "}";

    public static final String script2 = "import org.fidonet.jftn.share.Command\n" +
            "\n" +
            "class TossCommand implements Command<String[], String[]> {\n" +
            "    public static final String test_static = \"test_modify\"\n" +

            "    @Override\n" +
            "    String[] execute(String[] argv) {\n" +
            "        return [test_static]\n" +
            "    }\n" +
            "}\n" +
            "\n" +
            "\n" +
            "def register(serviceApi) {\n" +
            "    serviceApi.registerCommand(\"test\", new TossCommand())\n" +
            "}";

    @Test
    public void testReload() throws Exception {
        // Init JythonScriptManager
        ScriptEngine scriptManager = new GroovyScriptManager();
        // Init hook and command classes
        HookInterpreter hookInterpreter = new HookInterpreter();
        CommandCollection commands = new CommandCollection();
        CommandInterpreter commandInterpreter = new CommandInterpreter(commands);
        // Add to script scope "jftn" variable which have all above data
        try {
            scriptManager.registerScript("script1", script1.intern(), new JFtnScriptService(scriptManager, hookInterpreter, commandInterpreter));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //noinspection unchecked
        Command<String[], Object> command = commands.findCommandByName("test");
        TestCase.assertNotNull(command);
        String[] param = new String[]{"test"};
        Object result = command.execute(param);
        TestCase.assertEquals(1, ((String[]) result).length);
        TestCase.assertEquals("test", ((String[]) result)[0]);

        // Check static field in the script
        GroovyScriptEngineImpl engine = (GroovyScriptEngineImpl) scriptManager.getEngine();
        GroovyClassLoader groovyClassLoader = engine.getClassLoader();
        Class[] loadedClasses = groovyClassLoader.getLoadedClasses();
        Class<?> loadedClass = null;
        for (Class clazz : loadedClasses) {
            System.out.println("\"" + clazz.getCanonicalName() + "\"");
            if (clazz.getCanonicalName().equals("TossCommand")) {
                loadedClass = clazz;
            }
        }

        TestCase.assertNotNull(loadedClass);
        Field test_static = loadedClass.getField("test_static");
        TestCase.assertNotNull(test_static);
        TestCase.assertEquals("test", test_static.get(null));

        scriptManager.unregisterScript("script1", new JFtnScriptService(scriptManager, hookInterpreter, commandInterpreter));


        try {
            scriptManager.registerScript("script1", script2.intern(), new JFtnScriptService(scriptManager, hookInterpreter, commandInterpreter));
        } catch (Exception e) {
            e.printStackTrace();
        }
        loadedClasses = groovyClassLoader.getLoadedClasses();
        loadedClass = null;
        for (Class clazz : loadedClasses) {
            System.out.println("\"" + clazz.getCanonicalName() + "\"");
            if (clazz.getCanonicalName().equals("TossCommand")) {
                loadedClass = clazz;
            }
        }
        TestCase.assertNotNull(loadedClass);
        test_static = loadedClass.getField("test_static");
        TestCase.assertNotNull(test_static);
        TestCase.assertEquals("test_modify", test_static.get(null));

        List<String> loadedClassesNames = new ArrayList<String>();
        for (Class clazz : loadedClasses) {
            loadedClassesNames.add(clazz.getCanonicalName());
        }

        TestCase.assertEquals(3, loadedClassesNames.size());
        TestCase.assertEquals(true, loadedClassesNames.contains("TossCommand"));
    }

}
