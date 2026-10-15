package org.fidonet.nodelist;

import java.util.ArrayList;
import java.util.List;

/** FTS-5001 accepts both U,FLAG and the deprecated UFLAG spelling for the first user flag. */
final class DirectoryFlags {
    private DirectoryFlags() {
    }

    static List<String> normalized(List<String> raw) {
        List<String> result = new ArrayList<>();
        boolean user = false;
        for (String flag : raw) {
            if (!user && (flag.startsWith("U") || flag.startsWith("u"))) {
                user = true;
                flag = flag.substring(1);
            }
            if (!flag.isEmpty()) {
                result.add(flag);
            }
        }
        return result;
    }
}
