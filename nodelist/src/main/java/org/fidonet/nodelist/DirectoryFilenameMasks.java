package org.fidonet.nodelist;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class DirectoryFilenameMasks {
    private DirectoryFilenameMasks() {
    }

    /** Whether two validated '*'/'?' filename globs can select the same name. */
    static boolean overlaps(String first, String second) {
        String a = first.toUpperCase(Locale.ROOT);
        String b = second.toUpperCase(Locale.ROOT);
        ArrayDeque<int[]> pending = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        pending.add(new int[]{0, 0});
        while (!pending.isEmpty()) {
            int[] state = pending.remove();
            int i = state[0];
            int j = state[1];
            if (!seen.add(i * (b.length() + 1) + j)) {
                continue;
            }
            if (i == a.length() && j == b.length()) {
                return true;
            }
            char x = i < a.length() ? a.charAt(i) : 0;
            char y = j < b.length() ? b.charAt(j) : 0;
            if (x == '*') {
                pending.add(new int[]{i + 1, j});
            }
            if (y == '*') {
                pending.add(new int[]{i, j + 1});
            }
            if (x != 0 && y != 0 && (x == '*' || y == '*' || x == '?' || y == '?' || x == y)) {
                pending.add(new int[]{x == '*' ? i : i + 1, y == '*' ? j : j + 1});
            }
        }
        return false;
    }
}
