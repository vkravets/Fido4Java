package org.fidonet.binkp.common.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.binkp.common.commands.share.BinkCommand;
import org.fidonet.binkp.common.commands.share.CommandFactory;
import org.fidonet.binkp.common.commands.share.UnknownCommandException;
import org.fidonet.binkp.common.io.BinkData;

import org.junit.jupiter.api.Test;

public class CommandDispatchTest {
    @Test
    public void routesEveryKnownWireCommandAndAcceptsEmptyParameterlessMessages() throws Exception {
        CommandFactory factory = new CommandFactory();
        BinkCommand[] codes = {BinkCommand.M_ADR, BinkCommand.M_BSY, BinkCommand.M_EOB,
                BinkCommand.M_ERR, BinkCommand.M_FILE, BinkCommand.M_GET, BinkCommand.M_GOT,
                BinkCommand.M_OK, BinkCommand.M_PWD, BinkCommand.M_SKIP};
        Class<?>[] types = {ADRCommand.class, BSYCommand.class, EOBCommand.class, ERRCommand.class,
                FILECommand.class, GETCommand.class, GOTCommand.class, OKCommand.class,
                PWDCommand.class, SKIPCommand.class};
        for (int i = 0; i < codes.length; i++) {
            assertEquals(types[i], factory.createCommand(null, codes[i], "args").getClass());
        }
        assertTrue(factory.createCommand(null, BinkCommand.M_EOB, "") instanceof EOBCommand);
        assertTrue(factory.createCommand(null, BinkCommand.M_OK, null) instanceof OKCommand);
        for (String prefix : new String[]{"LOC", "NDL", "SYS", "TIME", "VER", "ZYZ", "OPT", "TRF",
                "unknown"}) {
            assertNotNull(factory.createCommand(null, BinkCommand.M_NUL, prefix + " value"));
        }
        assertNull(factory.createCommand(null, new BinkData(false, (byte) -1, new byte[]{1})));
        assertNull(factory.createCommand(null, new BinkData(true, (byte) 127, new byte[0])));
    }

    @Test
    public void rejectsMissingMandatoryArgumentsWithoutMisrouting() throws Exception {
        CommandFactory factory = new CommandFactory();
        for (BinkCommand code : new BinkCommand[]{BinkCommand.M_ADR, BinkCommand.M_FILE,
                BinkCommand.M_GET, BinkCommand.M_GOT, BinkCommand.M_SKIP}) {
            for (String args : new String[]{null, ""}) {
                try {
                    factory.createCommand(null, code, args);
                    fail("Accepted empty " + code);
                } catch (UnknownCommandException expected) {
                    assertTrue(expected.getMessage().contains(code.toString()));
                }
            }
        }
    }
}
