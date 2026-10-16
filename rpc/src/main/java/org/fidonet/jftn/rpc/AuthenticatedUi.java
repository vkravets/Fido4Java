package org.fidonet.jftn.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

/** Every UI method carries a bearer token as its first argument. Backend receives the owner. */
public final class AuthenticatedUi {
    private AuthenticatedUi() {
    }

    public static UiService.Iface wrap(UiSessions sessions, UiService.Iface backend) {
        for (Method method : UiService.Iface.class.getMethods()) {
            if (method.getParameterCount() == 0 || method.getParameterTypes()[0] != String.class) {
                throw new IllegalStateException("UI methods must declare a session token first");
            }
        }
        return (UiService.Iface) Proxy.newProxyInstance(UiService.Iface.class.getClassLoader(),
                                                        new Class<?>[]{UiService.Iface.class},
                                                        (proxy,
                                                         method,
                                                         args) -> invokeAuthenticated(sessions,
                                                                                      backend,
                                                                                      method,
                                                                                      args));
    }

    private static Object invokeAuthenticated(UiSessions sessions,
                                              UiService.Iface backend,
                                              Method method,
                                              Object[] args)
            throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(backend, args);
        }
        args[0] = sessions.require((String) args[0]);
        // Point mail access will be exposed with the future reader's scoped mailbox API.
        // Until then, existing node-wide UI operations remain administrator-only.
        sessions.requireAdminUser((String) args[0]);
        try {
            return method.invoke(backend, args);
        } catch (InvocationTargetException failure) {
            throw translateFailure(failure.getCause());
        }
    }

    private static UiError translateFailure(Throwable cause) {
        if (cause instanceof UiError) {
            return (UiError) cause;
        }
        if (cause instanceof IllegalArgumentException) {
            return new UiError("INVALID_ARGUMENT", cause.getMessage());
        }
        if (cause instanceof UnsupportedOperationException) {
            return new UiError("UNSUPPORTED", cause.getMessage());
        }
        return new UiError("OPERATION_FAILED", "Node operation failed; inspect the node log");
    }
}
