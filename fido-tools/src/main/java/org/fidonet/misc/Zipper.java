/******************************************************************************
 * Copyright (c) 2012-2015, Vladimir Kravets                                  *
 * All rights reserved.                                                       *
 *                                                                            *
 * Redistribution and use in source and binary forms, with or without         *
 * modification, are permitted provided that the following conditions are     *
 * met: Redistributions of source code must retain the above copyright notice,*
 * this list of conditions and the following disclaimer.                      *
 * Redistributions in binary form must reproduce the above copyright notice,  *
 * this list of conditions and the following disclaimer in the documentation  *
 * and/or other materials provided with the distribution.                     *
 * Neither the name of the Fido4Java nor the names of its contributors        *
 * may be used to endorse or promote products derived from this software      *
 * without specific prior written permission.                                 *
 *                                                                            *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"*
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,      *
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR     *
 * PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR          *
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,      *
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,        *
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS;*
 * OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,   *
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR    *
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE,             *
 * EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.                         *
 ******************************************************************************/

package org.fidonet.misc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Zipper {

    public static final int MAX_PACKET_BYTES = 64 * 1024 * 1024;
    private static final long MAX_BUNDLE_BYTES = 256L * 1024 * 1024;
    private static final int MAX_ENTRIES = 4096;

    private static final Logger logger = LoggerFactory.getLogger(Zipper.class.getName());

    public static LinkedList<PktTemp> unpackboundlfast(String bound) throws IOException {
        logger.info("Unpack and toss: {}", bound);
        long startTime = System.currentTimeMillis();
        LinkedList<PktTemp> res = new LinkedList<PktTemp>();

        try (ZipFile zip = new ZipFile(bound)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            byte[] buf = new byte[1024];
            long total = 0;
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (++count > MAX_ENTRIES) {
                    throw new IOException("Too many archive entries");
                }
                if (entry.isDirectory()) {
                    continue;
                }
                logger.debug("        {}", entry.getName());
                if (!entry.getName().toLowerCase(Locale.ROOT).endsWith(".pkt")) {
                    throw new IOException("Non-packet entry in mail bundle: " + entry.getName());
                }
                if (entry.getSize() > MAX_PACKET_BYTES) {
                    throw new IOException("Packet exceeds size limit");
                }
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream fis = zip.getInputStream(entry)) {
                    int readed;
                    while ((readed = fis.read(buf)) != -1) {
                        total += readed;
                        if (bytes.size() + readed > MAX_PACKET_BYTES || total > MAX_BUNDLE_BYTES) {
                            throw new IOException("Unpacked data exceeds size limit");
                        }
                        bytes.write(buf, 0, readed);
                    }
                }
                ByteBuffer resbuf = ByteBuffer.wrap(bytes.toByteArray())
                                              .order(ByteOrder.LITTLE_ENDIAN);
                PktTemp pkt = new PktTemp(entry.getName(), resbuf);
                res.push(pkt);
            }
        }
        logger.info("Unpacking was finished");
        logger.debug("Unpacking takes {}ms", System.currentTimeMillis() - startTime);
        return res;
    }
}
