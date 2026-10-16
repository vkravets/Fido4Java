package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.LinksInfo;
import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.common.SessionState;
import org.fidonet.binkp.common.config.StationConfig;
import org.fidonet.binkp.netty.plugin.NettyPlugin;
import org.fidonet.binkp.netty.plugin.Runner;
import org.fidonet.jftn.plugins.Plugin;
import org.fidonet.jftn.scheduler.Scheduler;
import org.fidonet.types.FTNAddr;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public class JFtnRunnerTest {
    @Test
    public void startsServicesPollsImmediatelyAndStopsInOrder() throws Exception {
        List<String> calls = new ArrayList<>();
        Runner runner = recordingRunner(calls);
        Scheduler scheduler = recordingScheduler(calls, false);
        SessionContext context = context(link("1:500/101"));
        JFtnRunner.runNode(runner,
                           scheduler,
                           context,
                           () -> calls.add("poll"),
                           0,
                           () -> calls.add("wait"),
                           () -> {
                               scheduler.stop();
                               runner.shutdown();
                           });
        assertEquals(Arrays.asList("bind",
                                   "start",
                                   "schedule",
                                   "poll",
                                   "wait",
                                   "stop-scheduler",
                                   "stop-runner"),
                     calls);
    }

    @Test
    public void startupFailureStillCleansUpServices() throws Exception {
        List<String> calls = new ArrayList<>();
        Runner runner = recordingRunner(calls);
        Scheduler scheduler = recordingScheduler(calls, true);
        try {
            JFtnRunner.runNode(runner, scheduler, context(), () -> {
            }, 0, () -> fail("Failed startup must not wait"), () -> {
                scheduler.stop();
                runner.shutdown();
            });
            fail("Expected startup failure");
        } catch (IllegalStateException expected) {
            assertEquals("Scheduler failed", expected.getMessage());
        }
        assertEquals(Arrays.asList("bind", "start", "stop-scheduler", "stop-runner"), calls);
    }

    @Test
    public void interruptionPreservesFlagAndCleansUpServices() throws Exception {
        List<String> calls = new ArrayList<>();
        Runner runner = recordingRunner(calls);
        Scheduler scheduler = recordingScheduler(calls, false);
        try {
            JFtnRunner.runNode(runner, scheduler, context(), () -> {
            }, 0, () -> {
                throw new InterruptedException("Stop requested");
            }, () -> {
                scheduler.stop();
                runner.shutdown();
            });
            fail("Expected interruption");
        } catch (InterruptedException expected) {
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(Arrays.asList("bind", "start", "schedule", "stop-scheduler", "stop-runner"),
                     calls);
    }

    @Test
    public void rejectsUnavailablePluginContexts() {
        Object available = new Object();
        assertSame(available, JFtnRunner.requireContext(new WeakReference<>(available), "test"));
        for (WeakReference<Object> reference : Arrays.asList(null,
                                                             new WeakReference<Object>(null))) {
            try {
                JFtnRunner.requireContext(reference, "test");
                fail("Missing service must fail");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("test"));
            }
        }
    }

    private static Runner recordingRunner(List<String> calls) {
        return new Runner() {
            @Override
            public void bindServer(SessionContext context, int port) {
                calls.add("bind");
            }

            @Override
            public void poll(Link link, SessionContext context) {
                calls.add("poll");
            }

            @Override
            public void shutdown() {
                calls.add("stop-runner");
                super.shutdown();
            }
        };
    }

    private static Scheduler recordingScheduler(List<String> calls, boolean fail) {
        return new Scheduler() {
            @Override
            public void start() {
                calls.add("start");
                if (fail) {
                    throw new IllegalStateException("Scheduler failed");
                }
            }

            @Override
            public String schedule(String cron, Runnable task, Boolean immediately) {
                assertEquals("*/10 * * * *", cron);
                assertTrue(immediately);
                calls.add("schedule");
                task.run();
                return "poll-links";
            }

            @Override
            public void stop() {
                calls.add("stop-scheduler");
            }
        };
    }

    private static SessionContext context(Link... links) {
        return new SessionContext(new StationConfig("Test", "Sysop", "Test", "BINKP", "1:500/100"),
                                  new LinksInfo(new ArrayList<>(Arrays.asList(links))));
    }

    @Test
    public void bundleDiscoversAndLoadsNettyTransport() throws Exception {
        Plugin<?> plugin = ServiceLoader.load(Plugin.class)
                                        .stream()
                                        .filter(provider -> provider.type() == NettyPlugin.class)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("Netty plugin missing from bundle"))
                                        .get();
        assertEquals(NettyPlugin.BINKP_PLUGIN_ID, plugin.getPluginInfo().getId());
        plugin.load();
        try {
            assertTrue(plugin.getContext().get() instanceof Runner);
        } finally {
            plugin.unload();
        }
        assertNull(plugin.getContext().get());
    }

    private static Link link(String address) {
        return new Link(new FTNAddr(address),
                        Collections.singletonList(new FTNAddr("1:500/100")),
                        "secret",
                        "127.0.0.1",
                        0);
    }
}
