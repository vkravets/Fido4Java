package org.fidonet.binkp;

/**
 * 
 * @author kreon
 * 
 */
public enum BinkpCommand {
    M_NUL(0), M_ADR(1), M_PWD(2), M_FILE(3), M_OK(4), M_EOB(5), M_GOT(6), M_ERR(7), M_BSY(8), M_GET(9), M_SKIP(10), M_PROCESS_FILE(
            99);
    private int cmd;

    private BinkpCommand(int cmd) {
        this.cmd = cmd;
    }

    public int getCmd() {
        return cmd;
    }

    public static BinkpCommand findCommand(int cmd) {
        for (BinkpCommand command : BinkpCommand.values()) {
            if (command.getCmd() == cmd) {
                return command;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return String.format("%s", this.name());
    }

}
