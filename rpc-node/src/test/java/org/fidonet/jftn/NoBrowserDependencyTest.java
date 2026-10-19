package org.fidonet.jftn;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** The complete server-side suite runs with no browser implementation on its classpath. */
class NoBrowserDependencyTest {
    @Test
    void browserImplementationAndSetupWereAbsent() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.fidonet.web.StaticUi"));
        assertThrows(ClassNotFoundException.class,
                     () -> Class.forName("org.fidonet.jftn.web.InitialSetup"));
    }
}
