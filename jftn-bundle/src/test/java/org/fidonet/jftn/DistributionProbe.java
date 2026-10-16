package org.fidonet.jftn;

import org.fidonet.jftn.plugins.Plugin;

import java.net.URL;
import java.util.HashSet;
import java.util.ServiceLoader;
import java.util.Set;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/** Executed in a fresh JVM with only packaged JARs and this test class on the classpath. */
public final class DistributionProbe {
    public static void main(String[] args) throws Exception {
        Set<String> plugins = new HashSet<>();
        for (Plugin<?> plugin : ServiceLoader.load(Plugin.class)) {
            plugins.add(plugin.getPluginInfo().getId());
        }
        if (!plugins.containsAll(Set.of("database",
                                        "schedule",
                                        "binkp_netty",
                                        "jam-bridge",
                                        "nodelist"))) {
            throw new IllegalStateException("Missing packaged plugins: " + plugins);
        }
        ScriptEngine groovy = new ScriptEngineManager().getEngineByName("groovy");
        if (groovy == null || !groovy.eval("40 + 2").equals(42)) {
            throw new IllegalStateException("Packaged Groovy engine did not work");
        }
        Class.forName("org.h2.Driver");
        URL script = DistributionProbe.class.getResource("/mail-scripts/areafix.groovy");
        if (script == null || !script.getProtocol().equals("file")) {
            throw new IllegalStateException("Mail scripts must be loaded from the external scripts directory");
        }
        System.out.println("Packaged plugins, Groovy, H2 and external scripts verified");
    }
}
