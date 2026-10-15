package org.fidonet.jftn.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class DateFormatCacheTest {
    @Test
    public void sharedFormatterSupportsConcurrentFormattingAndParsing() throws Exception {
        SafeSimpleDateFormat formatter = new SafeSimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                                                                  Locale.US);
        ExecutorService executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (int thread = 0; thread < 4; thread++) {
                final int sequence = thread;
                tasks.add(executor.submit(() -> {
                    SimpleDateFormat baseline = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS",
                                                                     Locale.US);
                    for (int i = 0; i < 100; i++) {
                        Date date = new Date(1700000000000L + sequence * 100000L + i);
                        String text = formatter.format(date);
                        assertEquals(baseline.format(date), text);
                        try {
                            assertEquals(date, formatter.parse(text));
                        } catch (ParseException failure) {
                            throw new AssertionError(failure);
                        }
                    }
                }));
            }
            for (Future<?> task : tasks) {
                task.get(5, TimeUnit.SECONDS);
            }
            Date date = new Date(1700000000000L);
            assertEquals(new SimpleDateFormat("dd/MM/yy HH:mm:ss.SSS").format(date),
                         new SafeSimpleDateFormat().format(date));
            try {
                formatter.parse("invalid");
                fail("Invalid date accepted");
            } catch (ParseException expected) {
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }

    @Test
    public void defaultLocaleIsCapturedPerFormatterAndDoesNotReuseOldLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            SafeSimpleDateFormat english = new SafeSimpleDateFormat("MMMM");
            String month = english.format(new Date(1700000000000L));
            Locale.setDefault(Locale.FRANCE);
            SafeSimpleDateFormat french = new SafeSimpleDateFormat("MMMM");
            assertNotEquals(month, french.format(new Date(1700000000000L)));
            assertEquals(month, english.format(new Date(1700000000000L)));
            assertEquals(month,
                         new SafeSimpleDateFormat("MMMM",
                                                  Locale.US).format(new Date(1700000000000L)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    public void collidingPatternHashesDoNotMixCachedFormats() {
        assertEquals("'Aa'".hashCode(), "'BB'".hashCode());
        assertEquals("Aa", new SafeSimpleDateFormat("'Aa'", Locale.US).format(new Date()));
        assertEquals("BB", new SafeSimpleDateFormat("'BB'", Locale.US).format(new Date()));
        assertEquals("Aa", new SafeSimpleDateFormat("'Aa'", Locale.US).format(new Date()));
    }

    @Test
    public void collidingLocaleHashesDoNotReuseAnotherLocaleEntry() {
        Locale first = new Locale("en", "", "Aa");
        Locale second = new Locale("en", "", "BB");
        assertNotEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        Date date = new Date(1700000000000L);
        for (Locale locale : new Locale[]{first, second, first}) {
            assertEquals(new SimpleDateFormat("MMMM", locale).format(date),
                         new SafeSimpleDateFormat("MMMM", locale).format(date));
        }
    }
}
