package org.fidonet.misc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class PacketArchiveTest {
    @TempDir
    public File temporary;

    @Test
    public void stagesBeforeProcessingAndPreservesPacketOrderWithoutRetainingPayloads()
            throws Exception {
        Path source = bundle("second.pkt");
        try (PacketArchive archive = MailBundleUnpacker.unpackToDisk(source.toString())) {
            Files.delete(source);
            assertEquals("second.pkt", archive.readNext().getName());
            PacketBuffer first = archive.readNext();
            assertEquals("nested/first.pkt", first.getName());
            assertEquals(123, first.getPkt().get());
            assertNull(archive.readNext());
        }
    }

    @Test
    public void rejectsEntireBundleBeforeExposingPacketsWhenALaterEntryIsInvalid()
            throws Exception {
        Path source = bundle("unexpected.txt");
        try (PacketArchive ignored = MailBundleUnpacker.unpackToDisk(source.toString())) {
            fail("Invalid bundle must not expose its first packet");
        } catch (IOException expected) {
            assertEquals("Non-packet entry in mail bundle: unexpected.txt", expected.getMessage());
        }
    }

    private Path bundle(String secondName) throws IOException {
        Path source = temporary.toPath().resolve("mail.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(source))) {
            entry(zip, "nested/first.pkt");
            entry(zip, secondName);
        }
        return source;
    }

    private void entry(ZipOutputStream zip, String name) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(123);
        zip.closeEntry();
    }
}
