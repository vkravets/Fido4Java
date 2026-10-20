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

package org.fidonet.db.plugin;

import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.echobase.MailStorage;
import org.fidonet.events.EventBus;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.plugins.PluginException;
import org.fidonet.jftn.plugins.PluginInformation;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.storage.StorageServices;
import org.fidonet.storage.StorageSession;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 8/7/13
 * Time: 9:53 AM
 */
public class DatabasePlugin implements Plugin<MailDatabaseFacade> {
    // The node stops mail workers before closing this connection; H2's parallel exit hook must not.
    public static final String DEFAULT_DATABASE_URL = "jdbc:h2:./database/jftn10;DB_CLOSE_ON_EXIT=FALSE";

    private MailDatabaseFacade manager;
    private StorageSession storage;
    private WeakReference<MailDatabaseFacade> context;

    @Override
    public PluginInformation getPluginInfo() {
        return new PluginInformation("database", 1, 0, "Store information in the database");
    }

    @Override
    public void init(PluginManager manager, EventBus eventBus) {

    }

    @Override
    public void load() throws PluginException {
        try {
            storage = StorageServices.file(Path.of("./database/jftn10"));
            manager = (MailDatabaseFacade) storage.require(MailStorage.class);
        } catch (IOException | RuntimeException failure) {
            if (storage != null) {
                try {
                    storage.close();
                } catch (IOException cleanup) {
                    failure.addSuppressed(cleanup);
                }
                storage = null;
            }
            throw new PluginException(failure);
        }

        context = new WeakReference<MailDatabaseFacade>(manager);
    }

    @Override
    public void unload() throws PluginException {
        if (storage != null) {
            try {
                storage.close();
            } catch (IOException failure) {
                throw new PluginException(failure);
            } finally {
                storage = null;
                manager = null;
            }
        }
        if (context != null) {
            context.clear();
        }
    }

    @Override
    public WeakReference<MailDatabaseFacade> getContext() {
        return context;
    }
}
