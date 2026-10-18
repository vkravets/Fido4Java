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
import org.fidonet.binkp.netty.plugin.NettyPlugin;
import org.fidonet.binkp.netty.plugin.Runner;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.db.DatabaseManager;
import org.fidonet.jftn.plugins.PluginManager;
import org.fidonet.jftn.poll.OutboundTransportFactory;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.scheduler.Scheduler;
import org.fidonet.jftn.scheduler.plugin.SchedulerPlugin;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.ui.NodeRpc;
import org.fidonet.jftn.web.InitialSetup;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.ref.WeakReference;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

public class JFtnRunner {

    private static final Logger logger = LoggerFactory.getLogger(JFtnRunner.class.getName());

    private static void Help() {
        //        System.out.println("java ftn usage:");
        //        System.out.println("jftn <action>:");
        //        System.out.println("    help - show this help");
        //        System.out.println("    toss - toss incoming echomail");
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--version")) {
            String version = JFtnRunner.class.getPackage().getImplementationVersion();
            System.out.println("JFtn " + (version == null ? "development" : version));
            return;
        }
        if (RobotAccessCommand.handle(args)) {
            return;
        }
        NodeDirectories.defaults(Path.of("."));
        logger.info("Starting JFtnNode...");
        InitialSetup.prepare(Path.of("jftn.conf"));
        final long starttime = System.currentTimeMillis();
        final PluginManager pluginManager = PluginManager.getInstance();
        try {
            withShutdownHook(waiter -> runApplication("jftn.conf",
                                                      pluginManager::loadPlugins,
                                                      pluginContexts(pluginManager),
                                                      pluginManager::unloadPlugins,
                                                      waiter));
        } catch (ParseConfigException e) {
            logger.error("Error during parsing config.", e);
            System.exit(1);
        }
        logger.debug("Finish working (time: {} sec)",
                     (System.currentTimeMillis() - starttime) / 1000.0);
    }

    interface PluginContexts {
        WeakReference<?> getContext(String name) throws Exception;

        default Iterable<MailExtensionFactory> mailExtensions() {
            return Collections.emptyList();
        }

        default PollService polling(JFtnConfig config, SessionContext context) {
            return new PollService(config, context);
        }
    }

    private static PluginContexts pluginContexts(PluginManager manager) {
        return new PluginContexts() {
            @Override
            public WeakReference<?> getContext(String name) throws Exception {
                return manager.getContext(name);
            }

            @Override
            public Iterable<MailExtensionFactory> mailExtensions() {
                return manager.findContexts(MailExtensionFactory.class);
            }
        };
    }

    static void runApplication(String configFile,
                               Runnable loadPlugins,
                               PluginContexts plugins,
                               Runnable unloadPlugins,
                               ShutdownWaiter waiter)
            throws Exception {
        JFtnConfig config = new JFtnConfig();
        config.load(configFile);
        NodeDirectories.configured(config);
        Runner runner;
        Scheduler scheduler;
        SessionContext context;
        MailService mail = null;
        NodeRpc rpc = null;
        PollService polls = null;
        try {
            loadPlugins.run();
            runner = Runner.class.cast(requireContext(plugins.getContext(NettyPlugin.BINKP_PLUGIN_ID),
                                                      "binkp"));
            scheduler = Scheduler.class.cast(requireContext(plugins.getContext(SchedulerPlugin.SCHEDULER_PLUGIN_ID),
                                                            "scheduler"));
            context = createSessionContext(config);
            DatabaseManager database = DatabaseManager.class.cast(requireContext(plugins.getContext("database"),
                                                                                 "database"));
            mail = new MailService(config, database, plugins.mailExtensions());
            mail.attach(context);
            mail.start();
            polls = plugins.polling(config, context);
            rpc = NodeRpc.open(config, database, mail, context, polls);
        } catch (Exception failure) {
            if (polls != null) {
                polls.close();
            }
            if (rpc != null) {
                rpc.close();
            }
            if (mail != null) {
                try {
                    mail.close();
                } catch (IOException closeFailure) {
                    failure.addSuppressed(closeFailure);
                }
            }
            unloadPlugins.run();
            throw failure;
        }
        MailService activeMail = mail;
        NodeRpc activeRpc = rpc;
        PollService activePolls = polls;
        runNode(runner,
                scheduler,
                context,
                activePolls::pollAll,
                Integer.parseInt(config.getValue("BinkpPort",
                                                 Integer.toString(Connector.BINK_PORT))),
                waiter,
                () -> stopServices(scheduler,
                                   runner,
                                   activeMail,
                                   activeRpc,
                                   activePolls,
                                   unloadPlugins));
    }

    private static void stopServices(Scheduler scheduler,
                                     Runner runner,
                                     MailService mail,
                                     NodeRpc rpc,
                                     PollService polls,
                                     Runnable unloadPlugins) {
        try (mail; polls) {
            try {
                if (rpc != null) {
                    rpc.close();
                }
            } finally {
                try {
                    scheduler.stop();
                } finally {
                    runner.shutdown();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            unloadPlugins.run();
        }
    }

    static SessionContext createSessionContext(JFtnConfig config) {
        new OutboundTransportFactory(config).validate();
        List<Link> links = new ArrayList<>();
        Set<String> addresses = new LinkedHashSet<>();
        for (String value : config.getValuesAsList("Link")) {
            Link link = new Link(value);
            links.add(link);
            for (FTNAddr address : link.getMyaddr()) {
                addresses.add(address.toString());
            }
        }
        StationConfig station = new StationConfig(config.getValue("System", "JFtn"),
                                                  config.getValue("SysOp"),
                                                  config.getValue("Location", ""),
                                                  config.getValue("NDL", "BINKP"),
                                                  String.join(" ", addresses));
        return new SessionContext(station, new LinksInfo(links));
    }

    interface ApplicationTask {
        void run(ShutdownWaiter waiter) throws Exception;
    }

    static void withShutdownHook(ApplicationTask task) throws Exception {
        CountDownLatch stopRequested = new CountDownLatch(1);
        CountDownLatch stopped = new CountDownLatch(1);
        Thread hook = new Thread(() -> {
            stopRequested.countDown();
            try {
                stopped.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "jftn-shutdown");
        Runtime.getRuntime().addShutdownHook(hook);
        try {
            task.run(stopRequested::await);
        } finally {
            stopped.countDown();
            try {
                Runtime.getRuntime().removeShutdownHook(hook);
            } catch (IllegalStateException shutdownInProgress) {
            }
        }
    }

    static <T> T requireContext(WeakReference<T> context, String name) {
        T service = context == null ? null : context.get();
        if (service == null) {
            throw new IllegalStateException("Unable to load " + name + " plugin");
        }
        return service;
    }

    interface ShutdownWaiter {
        void await() throws InterruptedException;
    }

    static void runNode(Runner runner,
                        Scheduler scheduler,
                        SessionContext context,
                        Runnable pollAll,
                        int port,
                        ShutdownWaiter waitForShutdown,
                        Runnable shutdown)
            throws InterruptedException {
        try {
            runner.bindServer(context, port);
            scheduler.start();
            scheduler.schedule("*/10 * * * *", pollAll, true);
            waitForShutdown.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } finally {
            shutdown.run();
        }
    }

}
