package org.fidonet.misc;

import org.fidonet.logger.ILogger;
import org.fidonet.logger.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Zipper {

    private static final ILogger logger = LoggerFactory.getLogger(Zipper.class.getName());

    public static LinkedList<PktTemp> unpackboundlfast(String bound) throws IOException {
        logger.info("Unpack and toss: " + bound);
        LinkedList<PktTemp> res = new LinkedList<PktTemp>();

        ZipFile zip = new ZipFile(bound);

        Enumeration entries = zip.entries();

        byte[] buf = new byte[1024];

        while (entries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) entries.nextElement();

            if (entry.isDirectory()) {
                //(new File(entry.getName())).mkdir();
                continue;
            }

            logger.debug("        " + entry.getName());

            ByteBuffer resbuf = ByteBuffer.allocate((int) entry.getSize());
            resbuf.order(ByteOrder.LITTLE_ENDIAN);
            resbuf.position(0);
            InputStream fis = zip.getInputStream(entry);
            while (fis.available() > 0) {
                int readed = fis.read(buf);
                resbuf.put(buf, 0, readed);
            }
            resbuf.position(0);
            PktTemp pkt = new PktTemp(entry.getName(), resbuf);
            res.push(pkt);
        }
        zip.close();
        logger.info("Unpacking was finished");
        return res;
    }

}
