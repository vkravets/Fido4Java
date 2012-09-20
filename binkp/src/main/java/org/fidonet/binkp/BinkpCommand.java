package org.fidonet.binkp;

/**
 * 
 * @author kreon
 * 
 */
public enum BinkpCommand {
    M_NUL((byte)0),
    M_ADR((byte)1),
    M_PWD((byte)2),
    M_FILE((byte)3),
    M_OK((byte)4),
    M_EOB((byte)5),
    M_GOT((byte)6),
    M_ERR((byte)7),
    M_BSY((byte)8),
    M_GET((byte)9),
    M_SKIP((byte)10),
    M_PROCESS_FILE((byte)99);

    private byte cmd;

    private BinkpCommand(byte cmd) {
        this.cmd = cmd;
    }

    public byte getCmd() {
        return cmd;
    }

    public static BinkpCommand findCommand(byte cmd) {
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
