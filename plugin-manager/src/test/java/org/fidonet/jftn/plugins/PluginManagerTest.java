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

package org.fidonet.jftn.plugins;

import junit.framework.TestCase;
import org.fidonet.jftn.plugins.mock.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/7/13
 * Time: 1:36 PM
 */
public class PluginManagerTest {

    @Test
    public void testDependenciesSortList() {

        PluginManager manager = PluginManager.getInstance();

        try {
            //     private Collection<? extends Plugin> sortDependencies(Collection<? extends Plugin> plugins) {
            Method sort = manager.getClass().getDeclaredMethod("sortDependencies", Iterable.class);
            sort.setAccessible(true);

            List<Plugin> plugins = new ArrayList<>();
            plugins.add(new PluginA());
            plugins.add(new PluginB());
            plugins.add(new PluginC());
            plugins.add(new PluginD());
            plugins.add(new PluginE());
            plugins.add(new PluginF());
            plugins.add(new PluginG());

            //noinspection unchecked
            Collection<? extends Plugin> sortPlugins = (Collection<? extends Plugin>) sort.invoke(manager, plugins);
            StringBuilder actual = new StringBuilder();
            for (Plugin plugin : sortPlugins) {
                actual.append(plugin.getPluginInfo().getId());
            }
            TestCase.assertEquals("GBCDFEA", actual.toString());
        }
        catch (NoSuchMethodException e) {
            e.printStackTrace();
            TestCase.fail();
        }
        catch (InvocationTargetException e) {
            e.printStackTrace();
            TestCase.fail();
        }
        catch (IllegalAccessException e) {
            e.printStackTrace();
            TestCase.fail();
        }
    }

}
