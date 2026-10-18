package org.fidonet.jftn;

import org.fidonet.config.JFtnConfig;
import org.fidonet.db.MailDatabaseFacade;
import org.fidonet.db.OrmManager;
import org.fidonet.echobase.jam.JamArea;
import org.fidonet.jftn.tosser.jam.JamArchiveImporter;
import org.fidonet.types.FTNAddr;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Locale;

/** Offline archive command. Does not start the transport or process outbound mail. */
public final class JamArchiveImport {
    private JamArchiveImport() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 5 || arguments.length > 6) {
            throw new IllegalArgumentException("Expected: config jdbc-url jam-path echo-name|- local-AKA [charset]");
        }
        JFtnConfig config = new JFtnConfig();
        config.load(arguments[0]);
        FTNAddr aka = new FTNAddr(arguments[4]);
        if (!config.getLocalAddresses().contains(aka)) {
            throw new IllegalArgumentException("Archive fallback AKA must be configured locally");
        }
        String area = "-".equals(arguments[3]) ? null : arguments[3].toLowerCase(Locale.ROOT);
        Charset charset = Charset.forName(arguments.length == 6 ? arguments[5] : "CP866");
        MailDatabaseFacade base = new MailDatabaseFacade(new OrmManager(arguments[1]));
        if (!base.open()) {
            throw new IOException("Unable to open archive target database");
        }
        try (MailRuntime service = new MailRuntime(config, base)) {
            if (area != null && !base.isAreaExists(area)) {
                throw new IllegalArgumentException("Create the SQL echo area before archive import: "
                        + area);
            }
            JamArea source = new JamArea(Path.of(arguments[2]), area, aka, charset);
            JamArchiveImporter.Result result = JamArchiveImporter.importInto(source, base);
            System.out.println("Imported: "
                               + result.getImported()
                               + "; duplicates: "
                               + result.getDuplicates());
        } finally {
            base.close();
        }
    }
}
