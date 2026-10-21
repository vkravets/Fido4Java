package org.fidonet.jftn.script;

import org.fidonet.config.JFtnConfig;
import org.fidonet.jftn.engine.script.GroovyScriptCompiler;
import org.fidonet.jftn.engine.script.PreparedGroovyScript;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Prepares isolated script bytecode, then registers scripts in configuration order. */
final class ExternalMailScripts {
    private static final Logger log = LoggerFactory.getLogger(ExternalMailScripts.class);
    private final List<PreparedGroovyScript> loaded = new ArrayList<>();
    private Path loadingDirectory;

    Path registrationDirectory() {
        if (loadingDirectory == null) {
            throw new IllegalStateException("An external script directory is required");
        }
        return loadingDirectory;
    }

    void loadAll(List<String> names, JFtnConfig config, MailAutomationRuntime api)
            throws Exception {
        long started = System.nanoTime();
        if (names == null || names.isEmpty())
            return;
        if (new HashSet<>(names).size() != names.size()) {
            throw new IllegalArgumentException("Duplicate MailScript");
        }
        String enabled = config.getValue("MailScriptCache", "true");
        if (!enabled.equals("true") && !enabled.equals("false")) {
            throw new IllegalArgumentException("MailScriptCache must be true or false");
        }
        int threads = compileThreads(config.getValue("MailScriptCompileThreads", "auto"),
                                     names.size());
        ClassLoader parent = Thread.currentThread().getContextClassLoader();
        if (parent == null)
            parent = getClass().getClassLoader();
        GroovyScriptCompiler compiler;
        try {
            compiler = new GroovyScriptCompiler(enabled.equals("true")
                    ? Path.of(config.getTmpDir(), "script-bytecode")
                    : null, parent);
        } catch (IOException failure) {
            log.warn("Cannot fingerprint script runtime; compiling without cache: {}",
                     failure.toString());
            compiler = new GroovyScriptCompiler(null, parent);
        }
        long initialized = System.nanoTime();
        List<PreparedGroovyScript> prepared = prepare(names, compiler, threads, parent);
        long compiled = System.nanoTime();
        for (int i = 0; i < names.size(); i++)
            register(names.get(i), prepared.get(i), api);
        log.info("Mail script startup: scripts={}, workers={}, fingerprintMs={}, prepareWallMs={}, registerWallMs={}, totalMs={}",
                 names.size(),
                 threads,
                 millis(initialized - started),
                 millis(compiled - initialized),
                 millis(System.nanoTime() - compiled),
                 millis(System.nanoTime() - started));
    }

    static int compileThreads(String setting, int count) {
        int requested;
        if (setting.equals("auto")) {
            int processors = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
            long memory = Math.max(1, (Runtime.getRuntime().maxMemory() / 1048576 - 256) / 128);
            requested = (int) Math.min(4, Math.min(processors, memory));
        } else {
            requested = Integer.parseInt(setting);
            if (requested < 1 || requested > 32) {
                throw new IllegalArgumentException("MailScriptCompileThreads must be auto or 1..32");
            }
        }
        return Math.max(1, Math.min(count, requested));
    }

    private List<PreparedGroovyScript> prepare(List<String> names,
                                               GroovyScriptCompiler compiler,
                                               int threads,
                                               ClassLoader parent)
            throws Exception {
        AtomicInteger sequence = new AtomicInteger();
        var executor = Executors.newFixedThreadPool(threads, task -> {
            Thread thread = new Thread(task, "mail-script-compile-" + sequence.incrementAndGet());
            thread.setContextClassLoader(parent);
            return thread;
        });
        try {
            List<Callable<PreparedGroovyScript>> tasks = new ArrayList<>();
            for (String name : names)
                tasks.add(() -> {
                    try {
                        return prepare(name, compiler);
                    } catch (Exception failure) {
                        throw new IOException("Cannot prepare MailScript " + name, failure);
                    }
                });
            List<PreparedGroovyScript> result = new ArrayList<>();
            for (Future<PreparedGroovyScript> future : executor.invokeAll(tasks)) {
                try {
                    result.add(future.get());
                } catch (ExecutionException failure) {
                    if (failure.getCause() instanceof Exception)
                        throw (Exception) failure.getCause();
                    throw failure;
                }
            }
            return result;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw failure;
        } finally {
            executor.shutdownNow();
            boolean interrupted = Thread.interrupted();
            while (!executor.isTerminated()) {
                try {
                    executor.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException failure) {
                    interrupted = true;
                }
            }
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    private PreparedGroovyScript prepare(String name, GroovyScriptCompiler compiler)
            throws Exception {
        long started = System.nanoTime();
        String content;
        if (name.startsWith("classpath:")) {
            try (InputStream input = MailAutomationRuntime.class.getResourceAsStream("/"
                    + name.substring(10))) {
                if (input == null) {
                    throw new IOException("Missing bundled script " + name);
                }
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } else {
            content = Files.readString(Path.of(name));
        }
        long read = System.nanoTime();
        PreparedGroovyScript prepared = compiler.prepare(name, content);
        log.info("Mail script preparation: name={}, cache={}, readMs={}, compileOrLoadMs={}",
                 name,
                 prepared.isCacheHit() ? "hit" : "miss",
                 millis(read - started),
                 millis(System.nanoTime() - read));
        return prepared;
    }

    private void register(String name, PreparedGroovyScript prepared, MailAutomationRuntime api)
            throws Exception {
        loadingDirectory = name.startsWith("classpath:")
                ? null
                : Path.of(name).toAbsolutePath().normalize().getParent();
        try {
            long started = System.nanoTime();
            prepared.evaluate();
            long evaluated = System.nanoTime();
            prepared.register(api);
            log.info("Mail script registration: name={}, evaluateMs={}, registerMs={}",
                     name,
                     millis(evaluated - started),
                     millis(System.nanoTime() - evaluated));
        } finally {
            loadingDirectory = null;
        }
        loaded.add(prepared);
    }

    private static long millis(long nanos) {
        return TimeUnit.NANOSECONDS.toMillis(nanos);
    }

    void close(MailAutomationRuntime api) throws IOException {
        IOException failure = null;
        for (int i = loaded.size() - 1; i >= 0; i--) {
            try {
                loaded.get(i).unload(api);
            } catch (Exception e) {
                if (failure == null) {
                    failure = new IOException("Cannot unload mail script", e);
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        loaded.clear();
        if (failure != null) {
            throw failure;
        }
    }
}
