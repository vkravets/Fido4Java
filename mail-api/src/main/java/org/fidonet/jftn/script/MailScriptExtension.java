package org.fidonet.jftn.script;

import java.io.IOException;

/** Optional service installed before external automation scripts register. */
public interface MailScriptExtension {
    void register(MailScripts api) throws IOException;
}
