package org.rctools.structure.art;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ART 树单元测试
 */
class ArtTreeTest {

    private ArtTree<Integer> artTree;

    @BeforeEach
    void setUp() {
        artTree = new ArtTree<>();
    }

    @Test
    void testPutAndGet() {
        artTree.put("hello", 1);
        artTree.put("world", 2);
        artTree.put("hello_world", 3);

        assertEquals(1, artTree.get("hello"));
        assertEquals(2, artTree.get("world"));
        assertEquals(3, artTree.get("hello_world"));
        assertNull(artTree.get("notexist"));
    }

    @Test
    void testPutByteArray() {
        artTree.put("key1".getBytes(), 100);
        artTree.put("key2".getBytes(), 200);

        assertEquals(100, artTree.get("key1".getBytes()));
        assertEquals(200, artTree.get("key2".getBytes()));
    }

    @Test
    void testContainsKey() {
        artTree.put("exists", 1);

        assertTrue(artTree.containsKey("exists"));
        assertFalse(artTree.containsKey("notexists"));
    }

    @Test
    void testSize() {
        assertEquals(0, artTree.size());
        assertTrue(artTree.isEmpty());

        artTree.put("key1", 1);
        artTree.put("key2", 2);
        artTree.put("key3", 3);

        assertEquals(3, artTree.size());
        assertFalse(artTree.isEmpty());
    }

    @Test
    void testUpdateExistingKey() {
        artTree.put("key", 1);
        assertEquals(1, artTree.get("key"));

        artTree.put("key", 2);
        assertEquals(2, artTree.get("key"));
    }

    @Test
    void testKeySet() {
        artTree.put("b", 2);
        artTree.put("a", 1);
        artTree.put("c", 3);

        Set<String> keys = artTree.keySet();
        assertEquals(3, keys.size());
        assertTrue(keys.contains("a"));
        assertTrue(keys.contains("b"));
        assertTrue(keys.contains("c"));
    }

    @Test
    void testValues() {
        artTree.put("key1", 1);
        artTree.put("key2", 2);
        artTree.put("key3", 3);

        Collection<Integer> values = artTree.values();
        assertEquals(3, values.size());
        assertTrue(values.contains(1));
        assertTrue(values.contains(2));
        assertTrue(values.contains(3));
    }

    @Test
    void testIterator() {
        artTree.put("key1", 1);
        artTree.put("key2", 2);
        artTree.put("key3", 3);

        int count = 0;
        for (ArtTree.Entry<Integer> entry : artTree) {
            assertNotNull(entry.getKey());
            assertNotNull(entry.getValue());
            count++;
        }
        assertEquals(3, count);
    }

    @Test
    void testRangeQuery() {
        artTree.put("key_001", 1);
        artTree.put("key_005", 2);
        artTree.put("key_010", 3);
        artTree.put("key_050", 4);
        artTree.put("key_100", 5);

        List<ArtTree.Entry<Integer>> result = artTree.range("key_005", "key_050");
        assertTrue(result.size() >= 2);
    }

    @Test
    void testPrefixSearch() {
        artTree.put("user_001", 1);
        artTree.put("user_002", 2);
        artTree.put("user_003", 3);
        artTree.put("admin_001", 4);
        artTree.put("user_004", 5);

        List<ArtTree.Entry<Integer>> result = artTree.prefixSearch("user_");
        assertEquals(4, result.size());

        for (ArtTree.Entry<Integer> entry : result) {
            assertTrue(entry.getKey().startsWith("user_"));
        }
    }

    @Test
    void testClear() {
        artTree.put("key1", 1);
        artTree.put("key2", 2);
        assertEquals(2, artTree.size());

        artTree.clear();
        assertEquals(0, artTree.size());
        assertTrue(artTree.isEmpty());
        assertNull(artTree.get("key1"));
    }

    @Test
    void testLongKeys() {
        StringBuilder longKey = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            longKey.append("a");
        }
        String key = longKey.toString();

        artTree.put(key, 100);
        assertEquals(100, artTree.get(key));
    }

    @Test
    void testSpecialCharacters() {
        artTree.put("key-with-dash", 1);
        artTree.put("key_with_underscore", 2);
        artTree.put("key.with.dot", 3);
        artTree.put("key/slash", 4);

        assertEquals(1, artTree.get("key-with-dash"));
        assertEquals(2, artTree.get("key_with_underscore"));
        assertEquals(3, artTree.get("key.with.dot"));
        assertEquals(4, artTree.get("key/slash"));
    }

    @Test
    void testEmptyKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> artTree.put("", 1));
    }

    @Test
    void testNullKeyThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> {
            String nullKey = null;
            artTree.put(nullKey, 1);
        });
    }

    @Test
    void testNullValueThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> artTree.put("key", null));
    }

    @Test
    void testChineseCharacters() {
        artTree.put("你好", 1);
        artTree.put("世界", 2);
        artTree.put("中文测试", 3);

        assertEquals(1, artTree.get("你好"));
        assertEquals(2, artTree.get("世界"));
        assertEquals(3, artTree.get("中文测试"));
    }

    @Test
    void testLargeDataset() {
        int size = 1000;
        for (int i = 0; i < size; i++) {
            artTree.put(String.format("key_%06d", i), i);
        }

        assertEquals(size, artTree.size());

        for (int i = 0; i < size; i++) {
            Integer value = artTree.get(String.format("key_%06d", i));
            assertEquals(i, value);
        }
    }

    @Test
    void testPrefixSearchWithEmptyPrefix() {
        artTree.put("a", 1);
        artTree.put("b", 2);
        artTree.put("c", 3);

        List<ArtTree.Entry<Integer>> result = artTree.prefixSearch("");
        assertEquals(3, result.size());
    }

    @Test
    void testPrefixSearchNonExistent() {
        artTree.put("user_001", 1);
        artTree.put("user_002", 2);

        List<ArtTree.Entry<Integer>> result = artTree.prefixSearch("admin_");
        assertTrue(result.isEmpty());
    }

    @Test
    void testRangeQueryWithSameStartAndEnd() {
        artTree.put("key_001", 1);
        artTree.put("key_002", 2);

        List<ArtTree.Entry<Integer>> result = artTree.range("key_001", "key_001");
        assertEquals(1, result.size());
    }

    @Test
    void testEntryGetValue() {
        artTree.put("key", 42);

        for (ArtTree.Entry<Integer> entry : artTree) {
            assertEquals("key", entry.getKey());
            assertEquals(42, entry.getValue());
        }
    }
}
