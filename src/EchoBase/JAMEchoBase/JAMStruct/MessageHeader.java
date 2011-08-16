package EchoBase.JAMEchoBase.JAMStruct;

import java.util.LinkedList;

/**
 * Created by IntelliJ IDEA.
 * User: toch
 * Date: 18.11.2010
 * Time: 12:48:56
 */
public class MessageHeader {
    public final byte[] signature = {'J', 'A', 'M', 0};
    public short revision = 1;
    public short reservedword = 0;
    public int SubfieldLen;
    public int TimesRead;
    public int MSGIDcrc;
    public int REPLYcrc;
    public int ReplyTo;
    public int Reply1st;
    public int Replynext;
    public int DateWritten;
    public int DateReceived;
    public int DateProcessed;
    public int MessageNumber;
    public int Attribute;
    public int Attribute2;
    public int Offset;
    public int TxtLen;
    public int PasswordCRC;
    public int Cost;
    public LinkedList<SubField> SubFieldList;

    public static final int MessageHeaderSize = 19 * 4;

    public MessageHeader() {
        SubFieldList = new LinkedList<SubField>();
    }

    public int getSubLen() {
        int result = 0;
        if (SubFieldList.size() != 0) {
            for (SubField sub : SubFieldList) {
                result += sub.datalen + 8;
            }
            return result;
        } else return 0;

    }

}
