package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.HashMap;
import java.util.Iterator;
import org.hisp.dhis.jsontree.JsonNode.Index;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

/**
 * Tests the internal {@link JsonTree} cache ({@code nodesByPath}) directly by inspecting the
 * map after each operation. Complements {@link JsonApiPerfTest}, which verifies the same
 * behaviour observably via identity checks. This class answers questions the identity checks
 * cannot, e.g. "did SKIP iteration add zero entries to the cache?".
 *
 * <p>Bootstraps a {@link JsonTree} manually so the caller owns the {@code nodesByPath} map and
 * can inspect it after each step.
 */
class JsonTreeCacheTest {

  private final HashMap<JsonPath, JsonNode> cache = new HashMap<>();

  /** Constructs a {@link JsonTree} with a caller-owned cache, mirroring {@link JsonTree#of}. */
  private JsonNode parse(@Language("json") String json, Index auto) {
    return JsonTree.of(json.toCharArray(), null, auto, cache);
  }

  // ---------------------------------------------------------------------------
  // Baseline
  // ---------------------------------------------------------------------------

  @Test
  void testFreshTree_HasOnlyRoot() {
    JsonNode tree = parse("[1,2,3]", Index.AUTO);
    assertEquals(1, cache.size(), "fresh tree has only the root entry");
    assertSame(tree, cache.get(JsonPath.SELF));
  }

  // ---------------------------------------------------------------------------
  // Iteration modes: cache size before / after
  // ---------------------------------------------------------------------------

  @Test
  void testSkip_DoesNotGrowCache() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2},{\"x\":3}]", Index.AUTO);
    int before = cache.size();
    for (JsonNode unused : tree.elements(Index.SKIP)) { /* consume */ }
    assertEquals(before, cache.size(), "SKIP must not grow the cache");
  }

  @Test
  void testAdd_CachesEveryElement() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2},{\"x\":3}]", Index.AUTO);
    int before = cache.size();
    int count = 0;
    for (JsonNode unused : tree.elements(Index.ADD)) { count++; }
    assertEquals(3, count);
    assertEquals(before + 3, cache.size(), "ADD caches each container element");
  }

  @Test
  void testAuto_DoesNotCachePrimitives() {
    JsonNode tree = parse("[1,2,3,4,5]", Index.AUTO);
    int before = cache.size();
    for (JsonNode unused : tree.elements()) { /* AUTO */ }
    assertEquals(before, cache.size(), "AUTO must not cache primitive elements");
  }

  @Test
  void testAuto_CachesContainers() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2},{\"x\":3}]", Index.AUTO);
    int before = cache.size();
    for (JsonNode unused : tree.elements()) { /* AUTO */ }
    assertEquals(before + 3, cache.size(), "AUTO caches container elements");
  }

  @Test
  void testCheck_DoesNotGrowCache() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2},{\"x\":3}]", Index.AUTO);
    int before = cache.size();
    for (JsonNode unused : tree.elements(Index.CHECK)) { /* consume */ }
    assertEquals(before, cache.size(), "CHECK must not grow the cache");
  }

  @Test
  void testCheck_ReusesExistingEntries() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2},{\"x\":3}]", Index.AUTO);
    // warm cache with ADD
    Iterator<JsonNode> added = tree.elements(Index.ADD).iterator();
    JsonNode first = added.next();
    int afterWarm = cache.size();
    // iterate with CHECK: cache size unchanged, cached instance returned
    Iterator<JsonNode> checked = tree.elements(Index.CHECK).iterator();
    assertSame(first, checked.next(), "CHECK returns the cached instance");
    assertEquals(afterWarm, cache.size(), "CHECK must not grow the cache");
  }

  // ---------------------------------------------------------------------------
  // Direct access: always caches, bypasses tree default
  // ---------------------------------------------------------------------------

  @Test
  void testDirectMember_GrowsCache() {
    JsonNode tree = parse("{\"a\":1,\"b\":2}", Index.SKIP);
    int before = cache.size();
    tree.member(Text.of("a"));
    assertEquals(before + 1, cache.size(),
        "direct member access caches regardless of tree default");
  }

  @Test
  void testDirectElement_GrowsCache_EvenForPrimitives() {
    JsonNode tree = parse("[1,2,3]", Index.SKIP);
    int before = cache.size();
    tree.element(0);
    assertEquals(before + 1, cache.size(),
        "direct element access caches even primitives");
  }

  // ---------------------------------------------------------------------------
  // Repeated iterations
  // ---------------------------------------------------------------------------

  @Test
  void testSkipTwice_NoGrowthFreshInstances() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2}]", Index.AUTO);
    int before = cache.size();
    JsonNode e1 = tree.elements(Index.SKIP).iterator().next();
    JsonNode e2 = tree.elements(Index.SKIP).iterator().next();
    assertEquals(before, cache.size(), "SKIP twice: no cache growth");
    assertNotSame(e1, e2, "SKIP twice: fresh instances each pass");
  }

  @Test
  void testAddTwice_SameInstances() {
    JsonNode tree = parse("[{\"x\":1},{\"x\":2}]", Index.AUTO);
    JsonNode e1 = tree.elements(Index.ADD).iterator().next();
    JsonNode e2 = tree.elements(Index.ADD).iterator().next();
    assertSame(e1, e2, "ADD twice: same cached instance");
  }
}