package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.SessionContext;
import org.fidonet.binkp.netty.plugin.NettyPlugin;
import org.fidonet.binkp.netty.plugin.Runner;
import org.fidonet.config.JFtnConfig;
import org.fidonet.config.ParseConfigException;
import org.fidonet.db.DatabaseManager;
import org.fidonet.db.OrmManager;
import org.fidonet.jftn.poll.PollService;
import org.fidonet.jftn.scheduler.Scheduler;
import org.fidonet.jftn.tosser.MailExtensionFactory;
import org.fidonet.jftn.tosser.MailProcessingExtension;
import org.fidonet.jftn.tosser.Tosser;
import org.fidonet.types.Link;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class RunnerStartupTest {
    @TempDir
    public File temporary;

    private File config() throws Exception {
        File file = Files.createFile(temporary.toPath().resolve("jftn.conf")).toFile();
        String root = temporary.getAbsolutePath().replace('\\', '/');
        Files.writeString(file.toPath(),
                          "Inbound="
                          + root
                          + "/inbound\n"
                          + "Tmp="
                          + root
                          + "/tmp\n"
                          + "EchoPath="
                          + root
                          + "/echo\n"
                          + "Outbound="
                          + root
                          + "/outbound\n"
                          + "SysOp=Configured Sysop\n"
                          + "System=Configured Station\n"
                          + "Location=Test Location\n"
                          + "NDL=TCP,BINKP\n"
                          + "Link=1:500/101,1:500/100,first,127.0.0.1\n"
                          + "Link=1:500/102,1:500/100,second,127.0.0.1\n");
        return file;
    }

    @Test
    public void loadsConfigurationStartsConfiguredLinksAndUnloadsPlugins() throws Exception {
        List<String> calls = new ArrayList<>();
        AtomicReference<SessionContext> bound = new AtomicReference<>();
        DatabaseManager database = new DatabaseManager(new OrmManager("jdbc:h2:mem:runnerMail"
                                                                      + System.nanoTime()));
        assertTrue(database.open());
        Runner runner = new Runner() {
            @Override
            public void bindServer(SessionContext context, int port) {
                bound.set(context);
                calls.add("bind");
            }

            @Override
            public void poll(Link link, SessionContext context) {
                calls.add("poll " + link.getPass());
            }
        };
        Scheduler scheduler = new Scheduler() {
            @Override
            public void start() {
                calls.add("start scheduler");
            }

            @Override
            public String schedule(String cron, Runnable task, Boolean immediately) {
                assertTrue(immediately);
                task.run();
                return "poll";
            }
        };
        try {
            JFtnRunner.withShutdownHook(waiter -> JFtnRunner.runApplication(config().getAbsolutePath(),
                                                                            () -> calls.add("load"),
                                                                            contexts(runner,
                                                                                     scheduler,
                                                                                     database,
                                                                                     calls),
                                                                            () -> calls.add("unload"),
                                                                            () -> calls.add("wait")));
            assertEquals(Arrays.asList("load",
                                       "bind",
                                       "start scheduler",
                                       "poll first",
                                       "poll second",
                                       "wait",
                                       "close extension",
                                       "unload"),
                         calls);
            assertEquals("Configured Station", bound.get().getStationConfig().getName());
            assertEquals("Configured Sysop", bound.get().getStationConfig().getSysopName());
            assertEquals("Test Location", bound.get().getStationConfig().getLocation());
            assertEquals("TCP,BINKP", bound.get().getStationConfig().getNDL());
            assertEquals("1:500/100.0", bound.get().getStationConfig().getAddress());
        } finally {
            runner.shutdown();
            database.close();
        }
    }

    private JFtnRunner.PluginContexts contexts(Runner runner,
                                               Scheduler scheduler,
                                               DatabaseManager database,
                                               List<String> calls) {
        return new JFtnRunner.PluginContexts() {
            @Override
            public WeakReference<?> getContext(String id) {
                if (id.equals(NettyPlugin.BINKP_PLUGIN_ID)) {
                    return new WeakReference<>(runner);
                }
                return new WeakReference<>(id.equals("database") ? database : scheduler);
            }

            @Override
            public PollService polling(JFtnConfig config, SessionContext context) {
                return new PollService(config, context) {
                    @Override
                    public void pollAll() {
                        config.getConfiguredLinks()
                              .forEach(link -> calls.add("poll " + link.getPass()));
                    }
                };
            }

            @Override
            public Iterable<MailExtensionFactory> mailExtensions() {
                return Collections.singletonList((config,
                                                  base) -> Optional.of(new MailProcessingExtension() {
                                                      @Override
                                                      public void beforeExport(Tosser tosser) {
                                                          assertEquals("Configured Station",
                                                                       config.getValue("System"));
                                                      }

                                                      @Override
                                                      public void afterExport() {
                                                      }

                                                      @Override
                                                      public void close() {
                                                          calls.add("close extension");
                                                      }
                                                  }));
            }
        };
    }

    @Test
    public void configurationFailureDoesNotLoadPlugins() throws Exception {
        List<String> calls = new ArrayList<>();
        try {
            JFtnRunner.runApplication(new File(temporary, "missing.conf").getAbsolutePath(),
                                      () -> calls.add("load"),
                                      id -> {
                                          throw new AssertionError("No lookup expected");
                                      },
                                      () -> calls.add("unload"),
                                      () -> fail("No wait expected"));
            fail("Expected config error");
        } catch (ParseConfigException expected) {
            assertTrue(calls.isEmpty());
        }
    }

    @Test
    public void missingPluginStillUnloadsLoadedPlugins() throws Exception {
        List<String> calls = new ArrayList<>();
        try {
            JFtnRunner.runApplication(config().getAbsolutePath(),
                                      () -> calls.add("load"),
                                      id -> null,
                                      () -> calls.add("unload"),
                                      () -> fail("No wait expected"));
            fail("Expected missing plugin");
        } catch (IllegalStateException expected) {
        }
        assertEquals(Arrays.asList("load", "unload"), calls);
    }

    @Test
    public void partialPluginLoadingFailureStillCleansUp() throws Exception {
        List<String> calls = new ArrayList<>();
        try {
            JFtnRunner.runApplication(config().getAbsolutePath(), () -> {
                calls.add("load");
                throw new IllegalStateException("Plugin failed");
            }, id -> {
                throw new AssertionError("No lookup expected");
            }, () -> calls.add("unload"), () -> fail("No wait expected"));
            fail("Expected load failure");
        } catch (IllegalStateException expected) {
            assertEquals("Plugin failed", expected.getMessage());
        }
        assertEquals(Arrays.asList("load", "unload"), calls);
    }

    @Test
    @Timeout(value = 10000, unit = TimeUnit.MILLISECONDS)
    public void interruptedShutdownWaitFinishesAndRemovesHook() throws Exception {
        CountDownLatch waiting = new CountDownLatch(1);
        AtomicReference<Throwable> outcome = new AtomicReference<>();
        Thread worker = new Thread(() -> {
            try {
                JFtnRunner.withShutdownHook(waiter -> {
                    waiting.countDown();
                    waiter.await();
                });
            } catch (Throwable failure) {
                outcome.set(failure);
            }
        });
        try {
            worker.start();
            assertTrue(waiting.await(5, TimeUnit.SECONDS));
            worker.interrupt();
            worker.join(5000);
            assertFalse(worker.isAlive());
            assertTrue(outcome.get() instanceof InterruptedException);
        } finally {
            worker.interrupt();
            worker.join(5000);
        }
    }

    @Test
    public void stationDefaultsAndAllConfiguredLocalAddressesArePreserved() throws Exception {
        JFtnConfig config = new JFtnConfig();
        File file = config();
        String text = Files.readString(file.toPath())
                           .replaceAll("(?m)^(System|Location|NDL)=.*\\n", "")
                           .replace("1:500/102,1:500/100", "1:500/102,1:500/103");
        Files.writeString(file.toPath(), text);
        config.load(file.getAbsolutePath());
        SessionContext context = JFtnRunner.createSessionContext(config);
        assertEquals("JFtn", context.getStationConfig().getName());
        assertEquals("", context.getStationConfig().getLocation());
        assertEquals("BINKP", context.getStationConfig().getNDL());
        assertEquals("1:500/100.0 1:500/103.0", context.getStationConfig().getAddress());
    }
}
