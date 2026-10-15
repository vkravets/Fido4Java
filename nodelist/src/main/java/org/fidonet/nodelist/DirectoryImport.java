package org.fidonet.nodelist;

import org.fidonet.config.BaseConfig;

/** Offline entry point; does not load plugins, connect to peers, toss mail or modify trusted links. */
public final class DirectoryImport {
    private DirectoryImport() {
    }

    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected directory configuration filename");
        }
        BaseConfig config = new BaseConfig() {
            @Override
            public boolean isValidate() {
                return true;
            }
        };
        config.load(arguments[0]);
        try (DirectoryService service = DirectoryService.open(config)) {
            for (DirectoryImportResult result : service.refresh()) {
                System.out.println(result.getSource()
                                   + ": "
                                   + result.getRecords()
                                   + " records, "
                                   + (result.isChanged() ? "imported" : "unchanged"));
            }
        }
    }
}
