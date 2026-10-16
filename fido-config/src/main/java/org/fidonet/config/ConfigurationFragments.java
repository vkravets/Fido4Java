package org.fidonet.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** File-only options override the main file without competing with UI-managed fields. */
final class ConfigurationFragments extends BaseConfig {
    private static final Set<String> UI_KEYS = Set.of("link",
                                                      "system",
                                                      "sysop",
                                                      "location",
                                                      "nodelisturl",
                                                      "nodelistcharset",
                                                      "rpchttporigin",
                                                      "uimailstorage",
                                                      "preservereceivedfiles");

    static void loadInto(BaseConfig target, Path source) throws IOException, ParseConfigException {
        Path directory = source.getParent().resolve("conf.d");
        if (!Files.exists(directory)) {
            return;
        }
        for (Path path : files(directory)) {
            ConfigurationFragments fragment = new ConfigurationFragments();
            fragment.load(path.toString());
            fragment.validateKeys(path);
            target.applyOverrides(fragment);
        }
    }

    private static List<Path> files(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            return paths.filter(path -> path.getFileName().toString().endsWith(".conf"))
                        .sorted()
                        .collect(Collectors.toList());
        }
    }

    private void validateKeys(Path path) throws ParseConfigException {
        for (String key : propertyNames()) {
            if (UI_KEYS.contains(key)) {
                throw new ParseConfigException("UI-managed option "
                                               + key
                                               + " is not allowed in "
                                               + path);
            }
        }
    }

    @Override
    public boolean isValidate() {
        return true;
    }
}
