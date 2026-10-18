package org.fidonet.nodelist;

import org.fidonet.config.IConfig;
import org.fidonet.jftn.script.ReceivedFile;
import org.fidonet.jftn.script.RobotRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicit per-source exceptions for full-publication header CRCs, never for diffs. */
final class DirectoryChecksumExceptions {
    private static final Logger LOG = LoggerFactory.getLogger(DirectoryChecksumExceptions.class);
    private final Map<String, List<String>> masks = new LinkedHashMap<>();

    DirectoryChecksumExceptions(IConfig config, Set<String> sources) {
        List<String> rules = config.getValuesAsList("DirectoryIgnoreCRC");
        if (rules == null) {
            return;
        }
        for (String rule : rules) {
            String[] fields = rule.split("\\|", -1);
            if (fields.length != 2 || !sources.contains(fields[0].trim())) {
                throw new IllegalArgumentException("Expected DirectoryIgnoreCRC=known-source-id|filename-mask");
            }
            String mask = fields[1].trim().toUpperCase(Locale.ROOT);
            if (!mask.matches("[A-Za-z0-9_.*?+-]{1,128}") || !mask.matches(".*[A-Za-z0-9].*")) {
                throw new IllegalArgumentException("CRC exception requires a filename mask with literal letters/digits, without directories");
            }
            List<String> values = masks.computeIfAbsent(fields[0].trim(), id -> new ArrayList<>());
            if (values.contains(mask)) {
                throw new IllegalArgumentException("Duplicate DirectoryIgnoreCRC rule");
            }
            values.add(mask);
        }
    }

    String definition(String id) {
        return String.join(",", masks.getOrDefault(id, List.of()));
    }

    DirectorySource select(DirectorySource source, String originalName, boolean diff) {
        if (!ReceivedFile.isSafeName(originalName)) {
            throw new IllegalArgumentException("Directory delivery requires a safe original filename");
        }
        if (diff) {
            return source;
        }
        for (String mask : masks.getOrDefault(source.getId(), List.of())) {
            if (RobotRequest.matches(mask, originalName)) {
                LOG.warn("Ignoring directory header CRC: source={}, file={}, configured mask={}; structural checks remain enabled",
                         source.getId(),
                         originalName,
                         mask);
                return source.withIgnoredChecksum(true);
            }
        }
        return source;
    }
}
