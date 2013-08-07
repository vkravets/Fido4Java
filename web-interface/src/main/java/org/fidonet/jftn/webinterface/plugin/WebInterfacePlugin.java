/******************************************************************************
 * Copyright (c) 2013, Vladimir Kravets                                       *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice, 
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" 
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; 
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.jftn.webinterface.plugin;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/7/13
 * Time: 10:38 AM
 */
public class WebInterfacePlugin implements Plugin {

    private Server server;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("web-interface", 1, 0, "Integrates Jetty application server.");
    }

    @Override
    public void init(PluginManager manager, EventBus eventBus) {
        // TODO: load configuration
    }

    @Override
    public void load() throws PluginException {
        try {
            server = new Server();
            ServerConnector serverConnector = new ServerConnector(server);
            serverConnector.setPort(80);
            server.setConnectors(new Connector[]{serverConnector});
        } catch (Exception e) {
            throw new PluginException(e);
        }
    }

    @Override
    public void unload() throws PluginException {
        try {
            server.stop();
        } catch (Exception e) {
            throw new PluginException(e);
        } finally {
            server = null;
        }
    }

    @Override
    public Object getContext() {
        return server;
    }
}
