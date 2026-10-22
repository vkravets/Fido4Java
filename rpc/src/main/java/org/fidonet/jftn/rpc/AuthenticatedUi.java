package org.fidonet.jftn.rpc;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Set;

/** Every UI method carries a bearer token as its first argument. Backend receives the owner. */
public final class AuthenticatedUi {
    private AuthenticatedUi() {
    }

    public static UiService.Iface wrap(UiSessions sessions, UiService.Iface backend) {
        return wrap(sessions, backend, false);
    }

    /** Enable only for backends enforcing address-scoped mail access. */
    public static UiService.Iface wrap(UiSessions sessions,
                                       UiService.Iface backend,
                                       boolean scopedMail) {
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
                                                                                      args,
                                                                                      scopedMail));
    }

    private static Object invokeAuthenticated(UiSessions sessions,
                                              UiService.Iface backend,
                                              Method method,
                                              Object[] args,
                                              boolean scopedMail)
            throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(backend, args);
        }
        args[0] = sessions.require((String) args[0]);
        if (!scopedMail || !mailOperation(method.getName())) {
            sessions.requireAdminUser((String) args[0]);
        }
        try {
            return method.invoke(backend, args);
        } catch (InvocationTargetException failure) {
            throw translateFailure(failure.getCause());
        }
    }

    private static boolean mailOperation(String name) {
        return Set.of("messages",
                      "searchMessages",
                      "areas",
                      "echoMessages",
                      "echoContext",
                      "message",
                      "markRead",
                      "deleteMessage",
                      "pendingNetmail",
                      "sendPendingNetmail",
                      "drafts",
                      "saveDraft",
                      "deleteDraft",
                      "send")
                  .contains(name);
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
