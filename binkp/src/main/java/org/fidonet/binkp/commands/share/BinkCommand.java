package org.fidonet.binkp.commands.share;

/**
 * Created by IntelliJ IDEA.
 * Author: Vladimir Kravets
 * E-Mail: vova.kravets@gmail.com
 * Date: 9/24/12
 * Time: 11:10 AM
 */
public enum BinkCommand {

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

        private BinkCommand(byte cmd) {
            this.cmd = cmd;
        }

        public byte getCmd() {
            return cmd;
        }

        public static BinkCommand findCommand(byte cmd) {
            for (BinkCommand command : BinkCommand.values()) {
                if (command.getCmd() == cmd) {
                    return command;
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return this.name();
        }
}
