package org.fidonet.jam.struct;

public class SubField {

    public short loID;
    public short hiID;
    public int datalen;
    public byte[] buffer;

    public SubField() {
        loID = 0;
        hiID = 0;
        datalen = 0;
    }

    public static class Types {
        final static short OADDRESS = 0;
        final static short DADDRESS = 1;
        final static short SENDERNAME = 2;
        final static short RECEIVERNAME = 3;
        final static short MSGID = 4;
        final static short REPLYID = 5;
        final static short SUBJECT = 6;
        final static short PID = 7;
        final static short TRACE = 8;
        final static short ENCLOSEDFILE = 9;
        final static short ENCLOSEDFILEWALIAS = 10;
        final static short ENCLOSEDFREQ = 11;
        final static short ENCLOSEDFILEWCARD = 12;
        final static short ENCLOSEDINDIRECTFILE = 13;
        final static short EMBINDAT = 1000;
        final static short FTSKLUDGE = 2000;
        final static short SEENBY2D = 2001;
        final static short PATH2D = 2002;
        final static short FLAGS = 2003;
        final static short TZUTCINFO = 2004;

        final static int MSG_LOCAL = (0x00000001);   // Msg created locally
        final static int MSG_INTRANSIT = (0x00000002);   // Msg is in-transit
        final static int MSG_PRIVATE = (0x00000004);   // Private
        final static int MSG_READ = (0x00000008);   // Read by addressee
        final static int MSG_SENT = (0x00000010);   // Sent to remote
        final static int MSG_KILLSENT = (0x00000020);   // Kill when sent
        final static int MSG_ARCHIVESENT = (0x00000040);   // Archive when sent
        final static int MSG_HOLD = (0x00000080);   // Hold for pick-up
        final static int MSG_CRASH = (0x00000100);   // Crash
        final static int MSG_IMMEDIATE = (0x00000200);   // Send Msg now, ignore restrictions
        final static int MSG_DIRECT = (0x00000400);   // Send directly to destination
        final static int MSG_GATE = (0x00000800);   // Send via gateway
        final static int MSG_FILEREQUEST = (0x00001000);   // File request
        final static int MSG_FILEATTACH = (0x00002000);   // File(s) attached to Msg
        final static int MSG_TRUNCFILE = (0x00004000);   // Truncate file(s) when sent
        final static int MSG_KILLFILE = (0x00008000);   // Delete file(s) when sent
        final static int MSG_RECEIPTREQ = (0x00010000);   // Return receipt requested
        final static int MSG_CONFIRMREQ = (0x00020000);   // Confirmation receipt requested
        final static int MSG_ORPHAN = (0x00040000);   // Unknown destination
        final static int MSG_ENCRYPT = (0x00080000);   // Msg text is encrypted          (1)
        final static int MSG_COMPRESS = (0x00100000);   // Msg text is compressed         (1)
        final static int MSG_ESCAPED = (0x00200000);   // Msg text is seven bit ASCII    (1)
        final static int MSG_FPU = (0x00400000);   // Force pickup
        final static int MSG_TYPELOCAL = (0x00800000);   // Msg is for local use only
        final static int MSG_TYPEECHO = (0x01000000);   // Msg is for conference distribution
        final static int MSG_TYPENET = (0x02000000);   // Msg is direct network mail
        final static int MSG_NODISP = (0x20000000);   // Msg may not be displayed to user
        final static int MSG_LOCKED = (0x40000000);   // Msg is locked, no editing possible
        final static int MSG_DELETED = (0x80000000);   // Msg is deleted

    }

}
