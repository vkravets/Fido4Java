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
                                                        (proxy, method, args) -> {
                                                            if (method.getDeclaringClass() == Object.class) {
                                                                return method.invoke(backend, args);
                                                            }
                                                            args[0] = sessions.require((String) args[0]);
                                                            try {
                                                                return method.invoke(backend, args);
                                                            } catch (InvocationTargetException failure) {
                                                                Throwable cause = failure.getCause();
                                                                if (cause instanceof UiError) {
                                                                    throw cause;
                                                                }
                                                                if (cause instanceof IllegalArgumentException) {
                                                                    throw new UiError("INVALID_ARGUMENT",
                                                                                      cause.getMessage());
                                                                }
                                                                if (cause instanceof UnsupportedOperationException) {
                                                                    throw new UiError("UNSUPPORTED",
                                                                                      cause.getMessage());
                                                                }
                                                                throw new UiError("OPERATION_FAILED",
                                                                                  "Node operation failed; inspect the node log");
                                                            }
                                                        });
    }
}
