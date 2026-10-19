package org.fidonet.jftn.rpc;

import org.fidonet.config.JFtnConfig;

import com.sun.net.httpserver.HttpHandler;

import java.util.Map;

/** Optional classpath-provided web content, hosted by the same HTTP listener as RPC. */
public interface HttpRouteProvider {
    Map<String, HttpHandler> routes();

    default Map<String, HttpHandler> routes(JFtnConfig config) {
        return routes();
    }
}
