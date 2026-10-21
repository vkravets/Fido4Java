package org.fidonet.jftn.engine.script;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

class GroovyScriptCompilerTest {
    @TempDir
    Path root;

    private GroovyScriptCompiler compiler(Path cache) throws Exception {
        return new GroovyScriptCompiler(cache, getClass().getClassLoader());
    }

    private void run(PreparedGroovyScript script, List<String> calls) throws Exception {
        script.evaluate();
        script.register(calls);
        script.unload(calls);
    }

    @Test
    void retainedBytecodeCreatesFreshInstancesAndInvalidatesChangedContent() throws Exception {
        String source = "int count = 0; def register(api) { api.add('register') }; "
                        + "def unload(api) { api.add('unload') }";
        PreparedGroovyScript first = compiler(root).prepare("sample", source);
        assertFalse(first.isCacheHit());
        List<String> calls = new ArrayList<>();
        run(first, calls);
        PreparedGroovyScript cached = compiler(root).prepare("sample", source);
        assertTrue(cached.isCacheHit());
        run(cached, calls);
        assertEquals(List.of("register", "unload", "register", "unload"), calls);
        PreparedGroovyScript changed = compiler(root).prepare("sample",
                                                              source.replace("register'",
                                                                             "changed'"));
        assertFalse(changed.isCacheHit());
        run(changed, calls);
        assertEquals("changed", calls.get(4));
    }

    @Test
    void parallelCompilationDoesNotRunCodeAndIsolatesHelperClasses() throws Exception {
        GroovyScriptCompiler compiler = compiler(root);
        String source = "class Helper { String value = '%s' }; "
                        + "def register(api) { api.add(new Helper().value) }; def unload(api) {}";
        var pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<PreparedGroovyScript>> tasks = List.of(() -> compiler.prepare("one",
                                                                                        String.format(source,
                                                                                                      "one")),
                                                                 () -> compiler.prepare("two",
                                                                                        String.format(source,
                                                                                                      "two")));
            List<String> calls = new ArrayList<>();
            for (var future : pool.invokeAll(tasks))
                run(future.get(), calls);
            assertEquals(List.of("one", "two"), calls);
            PreparedGroovyScript throwing = compiler.prepare("throwing",
                                                             "throw new IllegalStateException('evaluation')");
            assertThrows(IllegalStateException.class, throwing::evaluate);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void corruptAndUnwritableCachesFallBackWithoutHidingCompilationErrors() throws Exception {
        String source = "def register(api) { api.add('ok') }; def unload(api) {}";
        compiler(root).prepare("sample", source);
        Path jar;
        try (var files = Files.list(root)) {
            jar = files.findFirst().orElseThrow();
        }
        Files.writeString(jar, "damaged");
        PreparedGroovyScript repaired = compiler(root).prepare("sample", source);
        assertFalse(repaired.isCacheHit());
        assertTrue(compiler(root).prepare("sample", source).isCacheHit());
        Path blocked = root.resolve("not-a-directory");
        Files.writeString(blocked, "occupied");
        List<String> calls = new ArrayList<>();
        run(compiler(blocked).prepare("sample", source), calls);
        assertEquals(List.of("ok"), calls);
        assertThrows(Exception.class, () -> compiler(root).prepare("bad", "def broken( {"));
    }

    @Test
    void runtimeCodeChangesInvalidateEnvironmentFingerprint() throws Exception {
        Path classes = Files.createDirectory(root.resolve("classes"));
        Path code = classes.resolve("Example.class");
        Files.writeString(code, "before");
        try (URLClassLoader loader = new URLClassLoader(new URL[]{classes.toUri().toURL()},
                                                        getClass().getClassLoader())) {
            String before = GroovyScriptCompiler.fingerprint(loader);
            Files.writeString(code, "after");
            assertNotEquals(before, GroovyScriptCompiler.fingerprint(loader));
        }
    }

    @Test
    void classpathOrderInvalidatesEnvironmentFingerprint() throws Exception {
        Path first = Files.createDirectory(root.resolve("first"));
        Path second = Files.createDirectory(root.resolve("second"));
        URL one = first.toUri().toURL();
        URL two = second.toUri().toURL();
        try (URLClassLoader forward = new URLClassLoader(new URL[]{one, two},
                                                         getClass().getClassLoader());
                URLClassLoader reverse = new URLClassLoader(new URL[]{two, one},
                                                            getClass().getClassLoader())) {
            assertNotEquals(GroovyScriptCompiler.fingerprint(forward),
                            GroovyScriptCompiler.fingerprint(reverse));
        }
    }
}
