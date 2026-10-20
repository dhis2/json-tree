package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.NotNull;

import java.util.ArrayList;
import java.util.List;

import static java.util.Comparator.comparingInt;
import static org.hisp.dhis.jsontree.JsonNode.Index.SKIP;

/**
 * Private implementation of {@link JsonNodeOp}s.
 * 
 * @since 1.10
 * @author Jan Bernitt
 */
class JsonNodeOpPatch {

  /*
  Claim checking a patch...
   */

  /**
   * The paths an operation claims as an exclusive write region.
   * Two operations clash if a claim of one is an ancestor-or-self of a claim of the other.
   */
  static Iterable<JsonPath> claims(JsonNodeOp op) {
    if (op instanceof JsonNodeOp.Spread spread) {
      if (spread.value().isEmpty()) return List.of();
      if (spread.value().isObject())
        return spread.value().keys().map(key -> new JsonPath(spread.path(), key));
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

  private enum EditType {
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
  private record Edit(int start, int end, Text content, EditType type) {

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
    static final Text OPEN_BRACE = Text.of("{");
    static final Text CLOSE_BRACE = Text.of("}");
  }

  private static List<Edit> edits(JsonNode target, List<? extends JsonNodeOp> ops) {
    List<Edit> edits = new ArrayList<>(ops.size()*2);
    for (JsonNodeOp op : ops) {
      if (op instanceof JsonNodeOp.Remove r) {
        editsRemove(target, r, edits);
      } else if (op instanceof JsonNodeOp.Replace r) {
        editsReplace(target, r, edits);
      } else if (op instanceof JsonNodeOp.Insert i) {
        editsInsert(target, i, edits);
      } else if (op instanceof JsonNodeOp.Spread s) {
        editsSpread(target, s, edits);
      }
    }
    return edits;
  }

  private static void editsRemove(JsonNode target, JsonNodeOp.Remove op, List<Edit> edits) {
    JsonNode removed = target.getIfExists(op.path());
    if (removed == null) return;

    if (removed.isRoot()) {
      edits.add(new Edit(removed.startIndex(), removed.endIndex(),
          Edit.EMPTY, EditType.REMOVE));
      return;
    }

    JsonNode parent = removed.getParent();
    JsonPath predecessor = removed.predecessor();
    if (predecessor == null) {
      edits.add(new Edit(parent.startIndex() + 1, removed.endIndex(),
          Edit.EMPTY, EditType.REMOVE_WITH_COMMA));
      return;
    }

    JsonNode p = target.getRoot().get(predecessor);
    checkSourceAdjacent(removed, p, parent, op);
    edits.add(new Edit(p.endIndex(), removed.endIndex(),
        Edit.EMPTY, EditType.REMOVE));
  }

  private static void editsReplace(JsonNode target, JsonNodeOp.Replace of, List<Edit> edits) {
    JsonNode current = target.getIfExists(of.path());
    if (current == null) throw new JsonPatchException.JsonPatchEditException("Cannot replace non-existent node: " + of.path());
    editsReplace1(current, of.value(), edits);
  }

  private static void editsReplace1(JsonNode replaced, JsonNode value, List<Edit> edits) {
    edits.add(new Edit(replaced.startIndex(), replaced.endIndex(), value.getDeclaration(), EditType.REPLACE));
  }

  private static void editsInsert(JsonNode target, JsonNodeOp.Insert op, List<Edit> edits) {
    if (op.path().isEmpty()) {
      // insert at root = replace root
      edits.add(new Edit(target.startIndex(), target.endIndex(),
          op.value().getDeclaration(), EditType.REPLACE));
      return;
    }
    JsonPath parentPath = op.path().parentPath();
    JsonNode parent = parentPath.isEmpty() ? target : target.getIfExists(parentPath);
    Text name = op.path().segment();

    if (parent != null) {
      if (parent.type() == JsonNodeType.OBJECT) {
        JsonNode current = target.getIfExists(op.path());
        if (current != null) {
          editsReplace1(current, op.value(), edits);
        } else {
          editsInsertMemberAt(parent.endIndex() - 1, name, op.value().getDeclaration(), edits);
        }
      } else if (parent.type() == JsonNodeType.ARRAY) {
        editsInsertArr(parent, name, List.of(op.value().getDeclaration()), edits);
      } else {
        throw new JsonPatchException.JsonPatchEditException("Cannot insert into " + parent.type());
      }
      return;
    }
    if (parentPath.isEmpty())
      throw new JsonPatchException.JsonPatchEditException("Cannot auto-create parent at root: " + op.path());

    Parents created = editsInsertMissingParents(target, parentPath, edits);
    editsInsertMemberAt(created.offset(), name, op.value().getDeclaration(), edits);
    editsCloseParents(created, edits);
  }

  private static void editsInsertMemberAt(int p, Text key, Text valueJson, List<Edit> edits) {
    edits.add(new Edit(p, p, JsonBuilder.createString(key).getDeclaration(), EditType.INSERT_WITH_COMMA));
    edits.add(new Edit(p, p, Edit.COLON, EditType.INSERT));
    edits.add(new Edit(p, p, valueJson, EditType.INSERT));
  }

  private static void editsInsertArr(JsonNode arr, Text index, Iterable<Text> values, List<Edit> edits) {
    if (!index.isTextualInteger())
      throw new JsonPatchException.JsonPatchEditException(
          "Cannot insert into array at non-integer path segment `" + index + "`");
    int idx = index.parseInt();
    int size = arr.size();
    if (idx > size) throw new JsonPatchException.JsonPatchEditException("Index out of bounds: " + idx + " > " + size);
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

  private static void editsSpread(JsonNode target, JsonNodeOp.Spread op, List<Edit> edits) {
    JsonNode value = op.value();
    if (value.type() == JsonNodeType.OBJECT) {
      // RFC 6902 add semantics: existing key → replace value, new key → append member.
      // (Arrays take the opposite convention: insert at index i always means
      // "insert before i", never replace — hence the different branch below.)
      JsonNode obj = target.getIfExists(op.path());
      if (obj == null) {
        Parents created = editsInsertMissingParents(target, op.path(), edits);
        for (Text k : value.keys())
          editsInsertMemberAt(created.offset(), k, value.get(k).getDeclaration(), edits);
        editsCloseParents(created, edits);
        return;
      }
      if (obj.type() != JsonNodeType.OBJECT)
        throw new JsonPatchException.JsonPatchEditException("Not an object: " + op.path());
      for (Text k : value.keys()) {
        JsonNode nv = value.get(k);
        JsonNode existing = obj.getIfExists(k);
        if (existing != null) editsReplace1(existing, nv, edits);
        else editsInsertMemberAt(obj.endIndex() - 1, k, nv.getDeclaration(), edits);
      }
    } else if (value.type() == JsonNodeType.ARRAY) {
      JsonNode arr = getParent(op.path(), target);
      if (arr.type() != JsonNodeType.ARRAY) throw new JsonPatchException.JsonPatchEditException("Parent not array: " + op.path());
      Iterable<Text> elements = op.value().elements(SKIP).map(JsonNode::getDeclaration);
      editsInsertArr(arr, op.path().segment(), elements, edits);
    }
    else throw new JsonPatchException.JsonPatchEditException("Spread value must be object or array");
  }

  /*
  Handling parents, resolving and auto-creating edits
   */

  /** Offset at which the operation's own edits and the closing braces should be emitted, and how many {@code }} to emit. */
  private record Parents(int offset, int count) {}

  private static @NotNull JsonNode getParent(JsonPath path, JsonNode target) {
    JsonPath parentPath = path.parentPath();
    JsonNode parent = parentPath.isEmpty() ? target : target.getIfExists(parentPath);
    if (parent == null) throw new JsonPatchException.JsonPatchEditException("Parent does not exist: " + parentPath);
    return parent;
  }

  private static Parents editsInsertMissingParents(JsonNode target, JsonPath path, List<Edit> edits) {
    List<Text> missing = new ArrayList<>();
    JsonPath existing = path;
    while (!existing.isEmpty() && !target.exists(existing)) {
      missing.add(0, existing.segment());
      existing = existing.parentPath();
    }
    if (missing.isEmpty())
      throw new JsonPatchException.JsonPatchEditException("Auto-create called but parent exists: " + path);

    JsonNode ancestor = existing.isEmpty() ? target : target.getIfExists(existing);
    if (ancestor == null || ancestor.type() != JsonNodeType.OBJECT)
      throw new JsonPatchException.JsonPatchEditException(
          "Cannot auto-create parent: deepest existing ancestor is not an object: " + existing);

    for (Text seg : missing)
      if (seg.isTextualInteger())
        throw new JsonPatchException.JsonPatchEditException(
            "Cannot auto-create array parent at segment `" + seg + "`: " + path);

    int p = ancestor.endIndex() - 1;
    for (int i = 0; i < missing.size(); i++) {
      Text quoted = JsonBuilder.createString(missing.get(i)).getDeclaration();
      EditType keyType = i == 0 ? EditType.INSERT_WITH_COMMA : EditType.INSERT;
      edits.add(new Edit(p, p, quoted, keyType));
      edits.add(new Edit(p, p, Edit.COLON, EditType.INSERT));
      edits.add(new Edit(p, p, Edit.OPEN_BRACE, EditType.INSERT));
    }
    return new Parents(p, missing.size());
  }

  private static void editsCloseParents(Parents created, List<Edit> edits) {
    for (int i = 0; i < created.count(); i++)
      edits.add(new Edit(created.offset(), created.offset(), Edit.CLOSE_BRACE, EditType.INSERT));
  }

  /**
   * Guards against erasing the wrong span when the predecessor path resolves to
   * a non-adjacent source node. This happens when the target object has duplicate
   * keys: {@code member()} returns the first occurrence, but {@code predecessor()}
   * reflects the source-adjacent key, and that path resolves to the first
   * occurrence of that key, not the immediately-preceding one.
   *
   * <p>The check counts commas in the gap between the predecessor's value end and
   * the target's value start. For a valid object member removal, that gap is
   * exactly {@code ,"key":} — one comma, one colon, possible whitespace. Extra
   * commas mean there is source content between the two nodes that the edit span
   * would silently erase.
   *
   * <p>Arrays are immune: array element paths use unique numeric indexes, so
   * {@code path.chain(index - 1)} always resolves to the true source predecessor.
   */
  private static void checkSourceAdjacent(
      JsonNode removed, JsonNode predecessor, JsonNode parent, JsonNodeOp.Remove op) {
    if (parent.type() != JsonNodeType.OBJECT) return;

    // The gap [pred.endIndex, removed.startIndex) lies entirely within the
    // parent's span, so scan the parent's declaration rather than the root's —
    // parent.endIndex() only walks the parent, not the whole document.
    Text parentText = parent.getDeclaration();
    int parentStart = parent.startIndex();
    int from = predecessor.endIndex() - parentStart;
    int to = removed.startIndex() - parentStart;

    int commas = 0;
    for (int i = from; i < to && commas < 2; i++)
      if (parentText.charAt(i) == ',') commas++;

    if (commas != 1)
      throw new JsonPatchException.JsonPatchEditException(
          "Cannot remove reliably: predecessor of `" + op.path()
              + "` is not source-adjacent (duplicate keys in target)");
  }

  /*
  Applying edits...
   */

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
