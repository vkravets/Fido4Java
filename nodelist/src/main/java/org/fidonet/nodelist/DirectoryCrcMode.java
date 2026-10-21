package org.fidonet.nodelist;

import java.util.Locale;

/** Explicit checksum algorithms; historical UI/config values remain readable aliases. */
enum DirectoryCrcMode {
    XMODEM_CRC("XMODEM-CRC"), ARC_CRC("ARC-CRC");

    final String value;

    DirectoryCrcMode(String value) {
        this.value = value;
    }

    static DirectoryCrcMode parse(String value) {
        switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "XMODEM-CRC" :
            case "STANDARD" :
                return XMODEM_CRC;
            case "ARC-CRC" :
            case "NEW-POINT-KEEPER" :
                return ARC_CRC;
            default :
                throw new IllegalArgumentException("Expected XMODEM-CRC or ARC-CRC");
        }
    }
}
