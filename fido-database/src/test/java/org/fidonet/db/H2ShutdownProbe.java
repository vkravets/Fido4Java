package org.fidonet.db;

import org.fidonet.db.plugin.DatabasePlugin;

import org.h2.api.DatabaseEventListener;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Child JVM only: let H2's real exit hook finish before the application's last mail scan. */
public final class H2ShutdownProbe {
    private static final CountDownLatch H2_CLOSING = new CountDownLatch(1);
    private static final AtomicReference<Thread> H2_HOOK = new AtomicReference<>();
    // This connection is deliberately owned/closed by H2's real exit hook, not by the test hook.
    private static Connection witness;

    public static void main(String[] args) throws Exception {
        Files.createDirectories(Path.of("database"));
        String url = DatabasePlugin.DEFAULT_DATABASE_URL;
        if (args[0].equals("legacy")) {
            url = url.replace(";DB_CLOSE_ON_EXIT=FALSE", "");
        }
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager(url));
        if (!base.open()) {
            throw new IOException("Cannot open child mail database");
        }
        witness = DriverManager.getConnection("jdbc:h2:mem:shutdown-witness;DB_CLOSE_ON_EXIT=TRUE;"
                                              + "DATABASE_EVENT_LISTENER='"
                                              + Witness.class.getName()
                                              + "'");
        Runtime.getRuntime()
               .addShutdownHook(new Thread(() -> finishMail(base), "test-mail-shutdown"));
        System.exit(0);
    }

    private static void finishMail(MailDatabaseFacade base) {
        String result;
        try {
            awaitH2Hook();
            base.visitQueuedNetmail(delivery -> {
                throw new AssertionError("The isolated queue must be empty");
            });
            if (!base.createArea("shutdown-completed")) {
                throw new IOException("Cannot persist final application work");
            }
            result = "OK";
        } catch (Exception failure) {
            StringWriter trace = new StringWriter();
            failure.printStackTrace(new PrintWriter(trace));
            result = "FAILED\n" + trace;
        } finally {
            base.close();
        }
        try {
            Files.writeString(Path.of("shutdown-result.txt"), result);
        } catch (IOException failure) {
            failure.printStackTrace();
        }
    }

    private static void awaitH2Hook() throws Exception {
        if (!H2_CLOSING.await(5, TimeUnit.SECONDS)) {
            throw new IOException("H2 exit hook did not reach the witness database");
        }
        Thread hook = H2_HOOK.get();
        hook.join(5000);
        if (hook.isAlive()) {
            throw new IOException("H2 exit hook did not finish");
        }
    }

    public static final class Witness implements DatabaseEventListener {
        @Override
        public void closingDatabase() {
            H2_HOOK.set(Thread.currentThread());
            H2_CLOSING.countDown();
        }
    }
}
