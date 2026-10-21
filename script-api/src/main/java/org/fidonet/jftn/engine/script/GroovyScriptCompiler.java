package org.fidonet.jftn.engine.script;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovySystem;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.Phases;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.tools.GroovyClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Content-addressed bytecode cache. Instances are safe for concurrent independent compilations. */
public final class GroovyScriptCompiler {
    private static final Logger log = LoggerFactory.getLogger(GroovyScriptCompiler.class);
    private final ClassLoader parent;
    private final Path cache;
    private final String environment;

    public GroovyScriptCompiler(Path cache, ClassLoader parent) throws IOException {
        this.parent = parent;
        this.cache = cache;
        environment = cache == null ? "" : fingerprint(parent);
    }

    public PreparedGroovyScript prepare(String name, String content) throws Exception {
        String key = hex(digest().digest(("jftn-groovy-cache-v1\n" + environment + "\n" + name
                + "\n" + content).getBytes(StandardCharsets.UTF_8)));
        Path entry = cache == null ? null : cache.resolve(key + ".jar");
        if (entry != null && Files.exists(entry)) {
            try {
                return read(entry);
            } catch (Exception | LinkageError failure) {
                log.warn("Ignoring unusable script cache {}: {}", entry, failure.toString());
            }
        }
        Map<String, byte[]> classes = new HashMap<>();
        String main;
        CompilerConfiguration configuration = new CompilerConfiguration();
        try (GroovyClassLoader loader = new GroovyClassLoader(parent, configuration)) {
            CompilationUnit unit = new CompilationUnit(configuration, null, loader);
            SourceUnit source = unit.addSource("MailScript_" + key + ".groovy", content);
            unit.compile(Phases.CLASS_GENERATION);
            main = source.getAST().getScriptClassDummy().getName();
            for (GroovyClass compiled : unit.getClasses()) {
                classes.put(compiled.getName(), compiled.getBytes());
            }
        }
        PreparedGroovyScript result = instantiate(main, classes, false);
        if (entry != null) {
            try {
                write(entry, main, classes);
            } catch (IOException failure) {
                log.warn("Cannot publish script cache {}: {}", entry, failure.toString());
            }
        }
        return result;
    }

    private PreparedGroovyScript instantiate(String main, Map<String, byte[]> classes, boolean hit)
            throws ReflectiveOperationException {
        ClassLoader loader = new ClassLoader(parent) {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                byte[] bytes = classes.get(name);
                if (bytes == null)
                    throw new ClassNotFoundException(name);
                return defineClass(name, bytes, 0, bytes.length);
            }
        };
        return new PreparedGroovyScript(loader.loadClass(main).asSubclass(Script.class), hit);
    }

    private PreparedGroovyScript read(Path entry) throws Exception {
        try (JarInputStream input = new JarInputStream(Files.newInputStream(entry))) {
            Manifest manifest = input.getManifest();
            if (manifest == null)
                throw new IOException("Missing script cache manifest");
            String main = manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
            Map<String, byte[]> classes = new HashMap<>();
            JarEntry item;
            while ((item = input.getNextJarEntry()) != null) {
                String name = item.getName();
                if (!name.endsWith(".class"))
                    throw new IOException("Invalid cache entry");
                byte[] bytes = input.readAllBytes();
                Attributes attributes = manifest.getAttributes(name);
                if (attributes == null
                        || !hex(digest().digest(bytes)).equals(attributes.getValue("SHA-256"))) {
                    throw new IOException("Script cache checksum mismatch");
                }
                classes.put(name.substring(0, name.length() - 6).replace('/', '.'), bytes);
            }
            if (main == null || classes.size() != manifest.getEntries().size()) {
                throw new IOException("Incomplete script cache");
            }
            return instantiate(main, classes, true);
        }
    }

    private void write(Path entry, String main, Map<String, byte[]> classes) throws IOException {
        Files.createDirectories(cache);
        Path temporary = Files.createTempFile(cache, "compile-", ".tmp");
        try {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, main);
            for (Map.Entry<String, byte[]> compiled : classes.entrySet()) {
                Attributes attributes = new Attributes();
                attributes.putValue("SHA-256", hex(digest().digest(compiled.getValue())));
                manifest.getEntries()
                        .put(compiled.getKey().replace('.', '/') + ".class", attributes);
            }
            try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(temporary),
                                                              manifest)) {
                for (String name : new TreeSet<>(classes.keySet())) {
                    output.putNextEntry(new JarEntry(name.replace('.', '/') + ".class"));
                    output.write(classes.get(name));
                    output.closeEntry();
                }
            }
            try {
                Files.move(temporary,
                           entry,
                           StandardCopyOption.ATOMIC_MOVE,
                           StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                // An incomplete cache must never be visible to another startup.
                log.warn("Script cache requires atomic publication: {}", cache);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    /** Hash actual runtime code, not timestamps, so upgrades invalidate compiled scripts. */
    static String fingerprint(ClassLoader loader) throws IOException {
        MessageDigest digest = digest();
        digest.update((GroovySystem.getVersion() + "\n" + System.getProperty("java.version") + "\n"
                + System.getProperty("java.vendor")).getBytes(StandardCharsets.UTF_8));
        for (String name : new TreeSet<>(System.getProperties().stringPropertyNames())) {
            if (name.startsWith("groovy.") || name.equals("file.encoding")) {
                digest.update((name + "=" + System.getProperty(name)
                        + "\n").getBytes(StandardCharsets.UTF_8));
            }
        }
        LinkedHashSet<Path> roots = new LinkedHashSet<>();
        for (String item : System.getProperty("java.class.path")
                                 .split(java.io.File.pathSeparator)) {
            roots.add(Path.of(item).toAbsolutePath().normalize());
        }
        for (ClassLoader current = loader; current != null; current = current.getParent()) {
            digest.update(current.getClass().getName().getBytes(StandardCharsets.UTF_8));
            if (current instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) current).getURLs()) {
                    digest.update(url.toString().getBytes(StandardCharsets.UTF_8));
                    if (!url.getProtocol().equals("file")) {
                        throw new IOException("Unsupported script classpath URL " + url);
                    }
                    try {
                        roots.add(Path.of(url.toURI()).toAbsolutePath().normalize());
                    } catch (java.net.URISyntaxException failure) {
                        throw new IOException(failure);
                    }
                }
            }
        }
        for (Path root : roots) {
            digest.update(root.toString().getBytes(StandardCharsets.UTF_8));
            List<Path> files;
            if (Files.isDirectory(root)) {
                try (Stream<Path> paths = Files.walk(root)) {
                    files = paths.filter(Files::isRegularFile).filter(path -> {
                        String name = path.toString().replace('\\', '/');
                        return name.endsWith(".class") || name.endsWith(".groovy")
                                || name.contains("/META-INF/");
                    }).sorted().collect(Collectors.toList());
                }
            } else {
                files = Files.isRegularFile(root) ? List.of(root) : List.of();
            }
            for (Path file : files) {
                digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
                try (InputStream input = Files.newInputStream(file)) {
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) >= 0)
                        digest.update(buffer, 0, count);
                }
            }
        }
        return hex(digest.digest());
    }

    private static MessageDigest digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes)
            result.append(String.format("%02x", value & 255));
        return result.toString();
    }
}
