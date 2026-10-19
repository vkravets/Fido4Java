package org.fidonet.jftn.plugins;

import org.fidonet.events.EventBus;

import java.lang.ref.WeakReference;

/** An optional node service; preparation runs before storage and network startup. */
public interface ServicePlugin extends Plugin<ServicePlugin> {
    default boolean enabled(ServiceContext context) throws Exception {
        return true;
    }

    default void prepare(ServiceContext context) throws Exception {
    }

    AutoCloseable start(ServiceContext context) throws Exception;

    @Override
    default void init(PluginManager manager, EventBus eventBus) {
    }

    @Override
    default void load() {
    }

    @Override
    default void unload() {
    }

    @Override
    default WeakReference<ServicePlugin> getContext() {
        return new WeakReference<>(this);
    }
}
