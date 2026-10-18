package org.fidonet.jftn.script;

import java.io.IOException;

/** Optional service installed before external automation scripts register. */
public interface MailScriptExtension {
    void register(MailAutomationRuntime api) throws IOException;
}
