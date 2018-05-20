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

package org.fidonet.binkp.netty;

import junit.framework.TestCase;
import org.fidonet.binkp.netty.plugin.Runner;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginManager;
import org.junit.Ignore;
import org.junit.Test;

import java.lang.ref.WeakReference;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/6/13
 * Time: 10:54 AM
 */
public class NettyPluginTest {

    @Test
    public void pluginTest() throws InterruptedException {
        PluginManager manager = PluginManager.getInstance();
        manager.loadPlugins();
        boolean exception = false;
        WeakReference<Runner> binkp = null;
        try {
            binkp = manager.getContext("binkp_netty");
            TestCase.assertNotNull(binkp.get());
        } catch (PluginException ex) {
            exception = true;
            ex.printStackTrace();
        } catch (ClassCastException ex) {
            exception = true;
            ex.printStackTrace();
        } finally {
            PluginManager.getInstance().unloadPlugins();
        }
        TestCase.assertEquals(false, exception);
        TestCase.assertNull(binkp.get());
    }
}
