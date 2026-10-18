package org.fidonet.jftn.plugins;

/** Optional offline administration; implementations must not start node services. */
public interface OfflineCommand {
    boolean handle(String[] arguments) throws Exception;
}
