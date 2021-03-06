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

package org.fidonet.jftn;

import org.fidonet.binkp.common.Connector;
import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.mina3.Runner;
import org.fidonet.binkp.mina3.plugin.BinkPPlugin;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.scheduler.Scheduler;
import org.fidonet.jftn.scheduler.plugin.SchedulerPlugin;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedList;

public class JFtnRunner {

    private static final Logger logger = LoggerFactory.getLogger(JFtnRunner.class.getName());
    private static Boolean keepRun = true;

    private static void Help() {
//        System.out.println("java ftn usage:");
//        System.out.println("jftn <action>:");
//        System.out.println("    help - show this help");
//        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) throws Exception {

        logger.info("Starting JFtnNode...");
        final long starttime = System.currentTimeMillis();
        JFtnConfig config = new JFtnConfig();
        try {
            config.load("jftn.conf");
        } catch (ParseConfigException e) {
            logger.error("Error during parsing config.", e);
            System.exit(1);
        }

        final PluginManager pluginManager = PluginManager.getInstance();
        pluginManager.loadPlugins();

        final WeakReference<Runner> binkpRunner = pluginManager.getContext(BinkPPlugin.BINKP_PLUGIN_ID);
        LinksInfo linksInfo = new LinksInfo(new ArrayList<Link>());
        linksInfo.getLinks().add(new Link(FTNAddr.valueOf("2:467/70.0"),
                new LinkedList<FTNAddr>() {
                    {
                        add(FTNAddr.valueOf("2:467/1313.0"));
                        add(FTNAddr.valueOf("2:467/70.113"));
                    }
                },
                "mypassword",
                "f70n467.asuscomm.com",
                24554));
        final SessionContext binkpSession = new SessionContext(new StationConfig("Sly's Home", "Vladimir Kravets", "Ukraine, Odessa", "BINKP", "2:467/1313.0"), linksInfo);
        final Runner runner = binkpRunner.get();
        if (runner == null) {
            logger.error("Unable to load binkp plugin", new RuntimeException("Unable to load binkp plugin"));
            System.exit(1);
        }
        runner.bindServer(binkpSession, Connector.BINK_PORT);

        WeakReference<Scheduler> scheduler = pluginManager.getContext(SchedulerPlugin.SCHEDULER_PLUGIN_ID);
        Scheduler schedulerObject = scheduler.get();
        if (schedulerObject == null) {
            logger.error("Unable to load scheduler plugin", new RuntimeException("Unable to load schedular plugin"));
            System.exit(1);
        }
        schedulerObject.start();
        schedulerObject.schedule("*/10 * * * *", new Runnable() {
            @Override
            public void run() {
                poolLinks(binkpSession, runner);
            }
        }, true);

        final Thread mainThread = Thread.currentThread();

        // locked thread
        final Thread lockThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (keepRun) {
                    logger.debug("It's alive!");
                    try {
                        Thread.sleep(300000); // every 5 min
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }
            }
        });
        lockThread.setDaemon(true);
        lockThread.start();

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    pluginManager.unloadPlugins();
                    lockThread.interrupt();
                    mainThread.join();
                } catch (InterruptedException ignored) {

                }
                logger.debug("Finish working (time: {} sec)", (int) (System.currentTimeMillis() - starttime) / 1000.0);
                keepRun = false;
            }
        }));


        lockThread.join();
    }

    public static void poolLinks(SessionContext binkpSession, Runner runner) {
        logger.info("Pooling all uplinks");
        for (Link link : binkpSession.getLinksInfo().getLinks()) {
            try {
                runner.poll(link, binkpSession);
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }
    }
}
