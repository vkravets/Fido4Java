package org.fidonet.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.fidonet.db.objects.Echoarea;

import com.j256.ormlite.dao.Dao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public class PagingRegressionTest {
    private OrmManager database;
    private Dao<Echoarea, Object> dao;

    @BeforeEach
    public void open() throws Exception {
        database = new OrmManager("jdbc:h2:mem:paging" + System.nanoTime());
        database.connect();
        dao = database.getDao(Echoarea.class);
        for (int i = 0; i < 2005; i++) {
            Echoarea area = new Echoarea();
            area.setName("area" + i);
            dao.create(area);
        }
    }

    @AfterEach
    public void close() {
        database.disconnect();
    }

    private List<Long> read(long offset, long limit, boolean all) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (DatabasePagingIterator<Echoarea, Echoarea> iterator = new DatabasePagingIterator<>(dao.queryBuilder()
                                                                                                   .orderBy("id",
                                                                                                            true),
                                                                                                offset,
                                                                                                limit,
                                                                                                all)) {
            while (iterator.hasNext()) {
                assertTrue(iterator.hasNext());
                ids.add(iterator.next().getId());
            }
            assertFalse(iterator.hasNext());
        }
        return ids;
    }

    @Test
    public void readsAllPagesFromDefaultOffsetWithoutDuplicates() throws Exception {
        List<Long> ids = read(-1, 1000, true);
        assertEquals(2005, ids.size());
        assertEquals(2005, new HashSet<>(ids).size());
        assertEquals(Long.valueOf(2005), ids.get(2004));
    }

    @Test
    public void nonAlignedOffsetAndSinglePageWork() throws Exception {
        List<Long> ids = read(7, 1000, true);
        assertEquals(1998, ids.size());
        assertEquals(Long.valueOf(8), ids.get(0));
        assertEquals(1000, read(-1, 1000, false).size());
        assertEquals(2005, read(-1, -1, true).size());
        assertEquals(1998, read(7, -1, true).size());
        assertTrue(read(3000, 1000, true).isEmpty());
    }

    @Test
    public void rejectsZeroLimit() throws Exception {
        try {
            new DatabasePagingIterator<>(dao.queryBuilder(), 0, 0, true);
            fail("Expected invalid limit");
        } catch (IllegalArgumentException expected) {
        }
    }
}
