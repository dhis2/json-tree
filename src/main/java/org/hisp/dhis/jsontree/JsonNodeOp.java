package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchEditException;
import org.hisp.dhis.jsontree.internal.NotNull;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;
import static org.hisp.dhis.jsontree.JsonBuilder.createArray;
import static org.hisp.dhis.jsontree.JsonNode.Index.SKIP;

import java.util.ArrayList;
import java.util.List;

/**
 * {@linkplain JsonNodeOp}s are used to make bulk modifications using {@link
 * JsonNode#patch(List)}.
 *
 * <p>{@linkplain JsonNodeOp} is a path based operation that is not yet "bound" to target.
 *
 * <p>The order of operations made into a set does not matter. Any order has the same outcome when
 * applied to the same target.
 *
 * @author Jan Bernitt
 * @since 1.1 (renamed and revised since 1.10)
 */
public sealed interface JsonNodeOp {

  /**
   * @return the target of the operation
   */
  JsonPath path();

  JsonNode value();

  record Insert(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Replace(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Spread(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Remove(JsonPath path) implements JsonNodeOp {
    @Override
    public JsonNode value() {
      return JsonNode.NULL;
    }
  }

  /*
  Claim checking a patch...
   */

  /**
   * The paths an operation claims as an exclusive write region.
   * Two operations clash if a claim of one is an ancestor-or-self of a claim of the other.
   */
  static Iterable<JsonPath> claims(JsonNodeOp op) {
    if (op instanceof Spread spread) {
      if (spread.value.isEmpty()) return List.of();
      if (spread.value.isObject())
        return spread.value.keys().map(key -> new JsonPath(spread.path, key));
    }
    return List.of(op.path());
  }

  /**
   * @param ops set of patch operations
   * @implNote array merge inserts don't need special handling as it is irrelevant how many elements
   *     are inserted at the target index as each operation is independent and uniquely targets an
   *     insert position in the target array in its state before any change
   */
  static void checkPatch(List<? extends JsonNodeOp> ops) {
    for (int i = 0; i < ops.size(); i++) {
      for (int j = i + 1; j < ops.size(); j++) {
        JsonNodeOp a = ops.get(i);
        JsonNodeOp b = ops.get(j);

        for (JsonPath pa : claims(a)) {
          for (JsonPath pb : claims(b)) {
            if (isAncestorOrSelf(pa, pb) || isAncestorOrSelf(pb, pa)) {
              throw JsonPatchException.clash(ops, a, b);
            }
          }
        }
      }
    }
  }

  /** true if {@code a} equals {@code b} or is a strict ancestor of it */
  private static boolean isAncestorOrSelf(JsonPath a, JsonPath b) {
    return b.startsWith(a);   // your JsonPath's existing prefix test
  }

  /*
  Executing the edits of a patch...
   */

  //TODO make private

  enum EditType {
    REMOVE,
    REMOVE_WITH_COMMA,
    REPLACE,
    INSERT,             // raw content, no comma logic
    INSERT_WITH_COMMA,  // content, preceded by a comma iff the previous output char needs one
  }

  /**
   * @param start index in the original at which to add the given content
   * @param end index from which to continue in the original after adding the given content
   * @param content JSON to add when reaching the given start position in the original
   * @param type of this edit for debugging
   */
  record Edit(int start, int end, Text content, EditType type) {
    /** Buffer allocation: reserve room for the comma even if we might skip it. */
    int allocDiff() {
      int base = content.length() - erase();
      return type == EditType.INSERT_WITH_COMMA ? base + 1 : base;
    }

    int erase() {
      return end - start;
    }

    static final Text COMMA = Text.of(",");
    static final Text COLON = Text.of(":");
    static final Text EMPTY = Text.of("");
  }

  private static List<Edit> edits(JsonNode target, List<? extends JsonNodeOp> ops) {
    List<Edit> edits = new ArrayList<>(ops.size()*2);
    for (JsonNodeOp op : ops) {
      if (op instanceof Remove r) {
        removeEdits(target, r, edits);
      } else if (op instanceof Replace r) {
        replaceEdits(target, r, edits);
      } else if (op instanceof Insert i) {
        insertEdits(target, i, edits);
      } else if (op instanceof Spread s) {
        spreadEdits(target, s, edits);
      }
    }
    return edits;
  }

  private static void removeEdits(JsonNode target, JsonNodeOp.Remove op, List<Edit> edits) {
    JsonNode removed = target.getIfExists(op.path);
    if (removed == null) return;

    if (removed.isRoot()) {
      edits.add(new Edit(removed.startIndex(), removed.endIndex(),
          Edit.EMPTY, EditType.REMOVE));
      return;
    }

    JsonNode parent = removed.getParent();
    JsonPath predecessor = removed.predecessor();
    if (predecessor == null) {
      // first child (object member or array element):
      //   erase [after opening bracket, end of value], defer trailing comma blanking
      edits.add(new Edit(parent.startIndex() + 1, removed.endIndex(),
          Edit.EMPTY, EditType.REMOVE_WITH_COMMA));
    } else {
      // non-first: erase [end of predecessor, end of value]
      //   for objects this is `,"key":value`
      //   for arrays this is `,element`
      JsonNode p = target.getRoot().get(predecessor);
      edits.add(new Edit(p.endIndex(), removed.endIndex(),
          Edit.EMPTY, EditType.REMOVE));
    }
  }

  private static void replaceEdits(JsonNode target, JsonNodeOp.Replace of, List<Edit> edits) {
    JsonNode current = target.getIfExists(of.path);
    if (current == null) throw new JsonPatchEditException("Cannot replace non-existent node: " + of.path());
    replace1Edits(current, of.value(), edits);
  }

  private static void replace1Edits(JsonNode replaced, JsonNode value, List<Edit> edits) {
    edits.add(new Edit(replaced.startIndex(), replaced.endIndex(), value.getDeclaration(), EditType.REPLACE));
  }

  private static void insertEdits(JsonNode target, JsonNodeOp.Insert op, List<Edit> edits) {
    if (op.path().isEmpty()) {
      // insert at root = replace root
      edits.add(new Edit(target.startIndex(), target.endIndex(),
          op.value().getDeclaration(), EditType.REPLACE));
      return;
    }
    JsonNode parent = getParent(op.path, target);
    Text key = op.path.segment();
    if (parent.type() == JsonNodeType.OBJECT) {
      JsonNode current = target.getIfExists(op.path);
      if (current != null) {
        replace1Edits(current, op.value(), edits);
        return;
      }
      insertObj1Edits(parent, key, op.value, edits);
    } else if (parent.type() == JsonNodeType.ARRAY) {
      insertArrEdits(parent, key, List.of(op.value.getDeclaration()), edits);
    } else {
      throw new JsonPatchEditException("Cannot insert into " + parent.type());
    }
  }

  /** Emit edits to append a new member {@code "key":value} inside {@code obj}. */
  private static void insertObj1Edits(JsonNode obj, Text key, JsonNode value, List<Edit> edits) {
    int p = obj.endIndex() - 1;
    Text quotedKey = JsonBuilder.createString(key).getDeclaration();
    edits.add(new Edit(p, p, quotedKey, EditType.INSERT_WITH_COMMA));
    edits.add(new Edit(p, p, Edit.COLON, EditType.INSERT));
    edits.add(new Edit(p, p, value.getDeclaration(), EditType.INSERT));
  }

  private static void insertArrEdits(JsonNode arr, Text index, Iterable<Text> values, List<Edit> edits) {
    int idx = index.parseInt();
    int size = arr.size();
    if (idx > size) throw new JsonPatchEditException("Index out of bounds: " + idx + " > " + size);
    if (idx < size) {
      int p = arr.element(idx).startIndex();
      values.forEach(e -> {
        edits.add(new Edit(p, p, e, EditType.INSERT));
        edits.add(new Edit(p, p, Edit.COMMA, EditType.INSERT));
      });
    } else {
      // append
      int p = arr.endIndex() - 1;
      values.forEach(e -> edits.add(new Edit(p, p, e, EditType.INSERT_WITH_COMMA)));
    }
  }

  private static void spreadEdits(JsonNode target, JsonNodeOp.Spread op, List<Edit> edits) {
    JsonNode value = op.value();
    if (value.type() == JsonNodeType.OBJECT) {
      JsonNode obj = target.getIfExists(op.path);
      if (obj == null) throw new JsonPatchEditException("Spread target does not exist: " + op.path);
      if (obj.type() != JsonNodeType.OBJECT) throw new JsonPatchEditException("Not an object: " + op.path);
      for (Text k : value.keys()) {
        JsonNode e = value.get(k);
        JsonNode existing = obj.getIfExists(k);
        if (existing != null) {
          replace1Edits(existing, e, edits);
        } else {
          insertObj1Edits(obj, k, e, edits);
        }
      }
    } else if (value.type() == JsonNodeType.ARRAY) {
      JsonNode arr = getParent(op.path, target);
      if (arr.type() != JsonNodeType.ARRAY) throw new JsonPatchEditException("Parent not array: " + op.path);
      Iterable<Text> elements = op.value.elements(SKIP).map(JsonNode::getDeclaration);
      insertArrEdits(arr, op.path.segment(), elements, edits);
    }
    else throw new JsonPatchEditException("Spread value must be object or array");
  }

  private static @NotNull JsonNode getParent(JsonPath path, JsonNode target) {
    JsonPath parentPath = path.parentPath();
    JsonNode parent = parentPath.isEmpty() ? target : target.getIfExists(parentPath);
    if (parent == null) throw new JsonPatchEditException("Parent does not exist: " + parentPath);
    return parent;
  }

  //TODO when writing keys we can check if the key Text is backed by the same memory as the JSON
  // and if so we know there is no escaping - just need to add quotes around it

  static JsonNode patch(JsonNode target, List<? extends JsonNodeOp> patch) {
    if (patch.isEmpty()) return target;
    checkPatch(patch);
    List<Edit> edits = edits(target, patch);
    if (edits.isEmpty()) return target;

    edits.sort(comparingInt(Edit::start).thenComparingInt(Edit::erase));

    int allocLen = target.endIndex() - target.startIndex();
    for (Edit e : edits) allocLen += e.allocDiff();

    char[] dest = new char[allocLen];
    boolean pendingBlankComma = false;
    int destPos = 0;
    int srcPos = target.startIndex(); // source offset
    JsonNode root = target.getRoot();
    Text src = root.getDeclaration();
    for (Edit e : edits) {
      int copy = e.start() - srcPos;
      if (copy > 0) {
        src.copyTo(dest, destPos, srcPos - root.startIndex(), copy);
        if (pendingBlankComma) pendingBlankComma = !tryBlankComma(dest, destPos, copy);
        destPos += copy;
      }

      if (e.type() == EditType.INSERT_WITH_COMMA) {
        dest[destPos] = needsComma(dest, destPos) ? ',' : ' ';
        destPos++;
      }
      e.content().copyTo(dest, destPos);
      destPos += e.content().length();

      if (e.type() == EditType.REMOVE_WITH_COMMA) pendingBlankComma = true;

      srcPos = e.end();
    }
    int copy = target.endIndex() - srcPos;
    if (copy > 0) {
      src.copyTo(dest, destPos, srcPos - root.startIndex(), copy);
      if (pendingBlankComma) tryBlankComma(dest, destPos, copy);
    }

    return JsonTree.of(dest, null, JsonNode.Index.AUTO);
  }

  private static boolean needsComma(char[] dest, int offset) {
    int i = offset - 1;
    while (i >= 0 && JsonTree.isWhitespace(dest[i])) i--;
    return i >= 0 && dest[i] != '{' && dest[i] != '[';
  }

  /**
   * Attempts to resolve a pending {@link EditType#REMOVE_WITH_COMMA} by blanking
   * the first non-whitespace character in {@code dest[offset, offset+len)} if it
   * is a comma.
   *
   * @return {@code true} if the pending blank is resolved (a non-whitespace
   *         character was found — comma blanked, or non-comma meaning nothing to
   *         blank); {@code false} if the range was entirely whitespace, meaning
   *         the search must continue in the next copy.
   */
  private static boolean tryBlankComma(char[] dest, int offset, int len) {
    int end = offset + len;
    int i = offset;
    while (i < end && JsonTree.isWhitespace(dest[i])) i++;
    if (i == end) return false;
    if (dest[i] == ',') dest[i] = ' ';
    return true;
  }
}
