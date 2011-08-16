package misc;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Zipper {

    public static LinkedList<PktTemp> unpackboundlfast(String bound) throws IOException {
        Logger.Log("Unpack and toss: " + bound);
        ZipFile zip = null;
        LinkedList<PktTemp> res = new LinkedList<PktTemp>();

        try {
            zip = new ZipFile(bound);
        } catch (IOException e) {
            Logger.Log("Zip error " + bound + ':' + e.getMessage());
        }

        if (zip == null) {
            return null;
        }
        Enumeration entries = zip.entries();

        byte[] buf = new byte[1024];

        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();

            if (entry.isDirectory()) {
                //(new File(entry.getName())).mkdir();
                continue;
            }

            Logger.Log("        " + entry.getName());

            ByteBuffer resbuf = ByteBuffer.allocate((int) entry.getSize());
            resbuf.order(ByteOrder.LITTLE_ENDIAN);
            resbuf.position(0);
            InputStream fis = zip.getInputStream(entry);
            while (fis.available() > 0) {
                int readed = fis.read(buf);
                resbuf.put(buf, 0, readed);
            }
            resbuf.position(0);
            PktTemp pkt = new PktTemp();
            pkt.name = entry.getName();
            pkt.pkt = resbuf;

            res.push(pkt);
        }
        zip.close();
        return res;
    }

}
