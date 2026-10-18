package org.fidonet.jftn.rpc;

import org.apache.thrift.TMultiplexedProcessor;
import org.apache.thrift.TProcessor;

import java.util.LinkedHashMap;
import java.util.Map;

/** The TCP and browser endpoints expose the same handlers and authorization checks. */
final class RpcProcessors {
    private RpcProcessors() {
    }

    static Map<String, TProcessor> services(RpcServiceProvider handlers) {
        Map<String, TProcessor> services = new LinkedHashMap<>();
        services.put("api", new Api.Processor<>(handlers.getApiHandler()));
        services.put("login", new LoginService.Processor<>(handlers.getLoginHandler()));
        if (handlers.getSettingsHandler() != null) {
            services.put("settings",
                         new SettingsService.Processor<>(handlers.getSettingsHandler()));
        }
        if (handlers.getProfileHandler() != null) {
            services.put("profile", new ProfileService.Processor<>(handlers.getProfileHandler()));
        }
        services.put("messages", new MessageService.Processor<>(handlers.getMessageHandler()));
        if (handlers.getAuthHandler() != null) {
            services.put("auth", new AuthService.Processor<>(handlers.getAuthHandler()));
        }
        if (handlers.getUiHandler() != null) {
            services.put("ui", new UiService.Processor<>(handlers.getUiHandler()));
        }
        if (handlers.getStatisticsHandler() != null) {
            services.put("statistics",
                         new StatisticService.Processor<>(handlers.getStatisticsHandler()));
        }
        return services;
    }

    static TMultiplexedProcessor multiplexed(RpcServiceProvider handlers) {
        TMultiplexedProcessor processor = new TMultiplexedProcessor();
        services(handlers).forEach(processor::registerProcessor);
        return processor;
    }
}
