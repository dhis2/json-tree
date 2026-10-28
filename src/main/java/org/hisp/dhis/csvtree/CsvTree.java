package org.hisp.dhis.csvtree;

import java.util.HashMap;
import java.util.Map;
import org.hisp.dhis.jsontree.Text;

/**
 * Lazy CSV parser. The raw input is held once as a {@code char[]} and all nodes are
 * lightweight views (slices) into it.
 */
final class CsvTree {

    private CsvTree() {}

    /* ------------------------------------------------------------------ */
    /* Entry-point and node cache                                          */
    /* ------------------------------------------------------------------ */

    static CsvNode parse(CharSequence csv) {
        char[] buf = csv instanceof String s ? s.toCharArray() : Text.of(csv).toCharArray();
        State state = new State(buf, new HashMap<>());
        LazyDocument root = new LazyDocument(state, CsvPath.ROOT, 0);
        state.cache.put(CsvPath.ROOT, root);
        return root;
    }

    record State(char[] csv, Map<CsvPath, CsvNode> cache) {}

    /* ------------------------------------------------------------------ */
    /* Skip primitives                                                     */
    /* ------------------------------------------------------------------ */

    static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }
    static boolean isDigit(char c) { return c >= '0' && c <= '9'; }

    /** Skips a newline sequence and returns the position after it. */
    static int skipNewline(char[] csv, int i) {
        if (i < csv.length && csv[i] == '\r') i++;
        if (i < csv.length && csv[i] == '\n') i++;
        return i;
    }

    /** Position of the next row-terminating newline (respecting quotes/brackets). */
    static int skipToRowEnd(char[] csv, int start) {
        int depth = 0;
        boolean inQuote = false;
        int i = start;
        while (i < csv.length) {
            char c = csv[i];
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < csv.length && csv[i + 1] == '"') { i += 2; continue; }
                    inQuote = false;
                }
                i++;
            } else if (c == '"')  { inQuote = true; i++; }
            else if (c == '[')    { depth++;      i++; }
            else if (c == ']')    { depth--;      i++; }
            else if ((c == '\n' || c == '\r') && depth == 0) return i;
            else i++;
        }
        return i;
    }

    /** Position of the next column separator (respecting quotes/brackets). */
    static int skipToColumnEnd(char[] csv, int start, int rowEnd) {
        int depth = 0;
        boolean inQuote = false;
        int i = start;
        while (i < rowEnd) {
            char c = csv[i];
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < rowEnd && csv[i + 1] == '"') { i += 2; continue; }
                    inQuote = false;
                }
                i++;
            } else if (c == '"')  { inQuote = true; i++; }
            else if (c == '[')    { depth++;      i++; }
            else if (c == ']')    { depth--;      i++; }
            else if (c == ',' && depth == 0)      return i;
            else i++;
        }
        return i;
    }

    /* ------------------------------------------------------------------ */
    /* Locating the K-th item in a comma-separated region                  */
    /* ------------------------------------------------------------------ */

    static int[] findCommaSeparatedItem(char[] csv, int start, int end, int index) {
        int depth = 0;
        boolean inQuote = false;
        int itemStart = start;
        int itemIndex = 0;
        int i = start;
        while (i < end) {
            char c = csv[i];
            if (inQuote) {
                if (c == '"') {
                    if (i + 1 < end && csv[i + 1] == '"') { i += 2; continue; }
                    inQuote = false;
                }
                i++;
            } else if (c == '"')  { inQuote = true; i++; }
            else if (c == '[')    { depth++;      i++; }
            else if (c == ']')    { depth--;      i++; }
            else if (c == ',' && depth == 0) {
                if (itemIndex == index) return trim(csv, itemStart, i);
                itemIndex++;
                i++;
                itemStart = i;
            } else i++;
        }
        return itemIndex == index ? trim(csv, itemStart, i) : null;
    }

    static int[] trim(char[] csv, int start, int end) {
        while (start < end && isWhitespace(csv[start])) start++;
        while (end > start && isWhitespace(csv[end - 1])) end--;
        return new int[] { start, end };
    }

    /* ------------------------------------------------------------------ */
    /* Rows                                                                */
    /* ------------------------------------------------------------------ */

    static int[] findRow(char[] csv, int index) {
        int i = 0, n = 0;
        while (i < csv.length) {
            int rowEnd = skipToRowEnd(csv, i);
            int next = skipNewline(csv, rowEnd);
            if (rowEnd > i) {
                if (n == index) return new int[] { i, rowEnd };
                n++;
            }
            i = next;
            if (next == rowEnd) break;
        }
        return null;
    }

    static int countRows(char[] csv) {
        int i = 0, n = 0;
        while (i < csv.length) {
            int rowEnd = skipToRowEnd(csv, i);
            int next = skipNewline(csv, rowEnd);
            if (rowEnd > i) n++;
            i = next;
            if (next == rowEnd) break;
        }
        return n;
    }

    /* ------------------------------------------------------------------ */
    /* Type detection for a scalar/array span                              */
    /* ------------------------------------------------------------------ */

    static CsvNodeType detectType(char[] csv, int start, int end) {
        int[] t = trim(csv, start, end);
        start = t[0]; end = t[1];
        if (start >= end) return CsvNodeType.STRING;
        char c = csv[start];
        if (c == '[') return CsvNodeType.ARRAY;
        if (c == '"') return CsvNodeType.STRING;
        int len = end - start;
        if (len == 4 && matches(csv, start, "true"))  return CsvNodeType.BOOLEAN;
        if (len == 5 && matches(csv, start, "false")) return CsvNodeType.BOOLEAN;
        if (c == '-' || c == '+' || isDigit(c)) {
            boolean numeric = true;
            for (int i = start + 1; i < end; i++) {
                char ch = csv[i];
                if (!isDigit(ch) && ch != '.' && ch != 'e' && ch != 'E' && ch != '+' && ch != '-') {
                    numeric = false; break;
                }
            }
            if (numeric) return CsvNodeType.NUMBER;
        }
        return CsvNodeType.STRING;
    }

    private static boolean matches(char[] csv, int start, String word) {
        for (int i = 0; i < word.length(); i++) if (csv[start + i] != word.charAt(i)) return false;
        return true;
    }

    /* ------------------------------------------------------------------ */
    /* Shared lazy-node base                                               */
    /* ------------------------------------------------------------------ */

    abstract static class LazyCsvNode implements CsvNode {

        final State state;
        final CsvPath path;
        final int start;
        int end = -1;

        LazyCsvNode(State state, CsvPath path, int start) {
            this.state = state; this.path = path; this.start = start;
        }

        @Override public CsvPath path() { return path; }
        @Override public int startIndex() { return start; }
        @Override public int endIndex() {
            if (end < 0) end = computeEnd();
            return end;
        }
        @Override public Text getDeclaration() {
            return Text.of(state.csv, start, endIndex() - start);
        }
        @Override public CsvNode getRoot() { return state.cache.get(CsvPath.ROOT); }
        @Override public CsvNode getParent() {
            if (path.isEmpty()) return this;
            //TODO
            return null;
        }

        protected abstract int computeEnd();

        @Override public boolean equals(Object o) {
            return o instanceof LazyCsvNode n && n.state == state && n.path.equals(path);
        }
        @Override public int hashCode() {
            return System.identityHashCode(state) * 31 + path.hashCode();
        }
        @Override public String toString() { return getDeclaration().toString(); }
    }

    /* ------------------------------------------------------------------ */
    /* DOCUMENT                                                            */
    /* ------------------------------------------------------------------ */

    static final class LazyDocument extends LazyCsvNode {
        LazyDocument(State s, CsvPath p, int start) { super(s, p, start); }

        @Override public CsvNodeType type() { return CsvNodeType.ARRAY; }
        @Override protected int computeEnd() { return state.csv.length; }

        @Override public int size() { return countRows(state.csv); }

        @Override public CsvNode get(int index) {
            CsvNode n = getIfExists(index);
            if (n == null) throw CsvPathException.noSuchChild(path, index, size());
            return n;
        }

        @Override public CsvNode getIfExists(int index) {
            if (index < 0) return null;
            CsvPath cp = path.chain(index);
            CsvNode cached = state.cache.get(cp);
            if (cached != null) return cached;
            int[] r = findRow(state.csv, index);
            if (r == null) return null;
            return state.cache.computeIfAbsent(cp, k -> new LazyRow(state, k, r[0]));
        }
    }

    /* ------------------------------------------------------------------ */
    /* ROW                                                                 */
    /* ------------------------------------------------------------------ */

    static final class LazyRow extends LazyCsvNode {
        LazyRow(State s, CsvPath p, int start) { super(s, p, start); }

        @Override public CsvNodeType type() { return CsvNodeType.ARRAY; }
        @Override protected int computeEnd() { return skipToRowEnd(state.csv, start); }

        @Override public int size() {
            int end = endIndex();
            if (end <= start) return 0;
            int n = 0, i = start;
            while (i < end) {
                int colEnd = skipToColumnEnd(state.csv, i, end);
                n++;
                i = colEnd + 1;
                if (colEnd >= end) break;
            }
            return n;
        }

        @Override public CsvNode get(int index) {
            CsvNode n = getIfExists(index);
            if (n == null) throw CsvPathException.noSuchChild(path, index, size());
            return n;
        }

        @Override public CsvNode getIfExists(int index) {
            if (index < 0) return null;
            CsvPath cp = path.chain(index);
            CsvNode cached = state.cache.get(cp);
            if (cached != null) return cached;
            int end = endIndex();
            int[] span = findCommaSeparatedItem(state.csv, start, end, index);
            if (span == null) return null;
            return state.cache.computeIfAbsent(cp, k -> new LazyColumn(state, k, span[0]));
        }
    }

    /* ------------------------------------------------------------------ */
    /* COLUMN (cell)                                                       */
    /* ------------------------------------------------------------------ */

    static final class LazyColumn extends LazyCsvNode {
        LazyColumn(State s, CsvPath p, int start) { super(s, p, start); }

        @Override public CsvNodeType type() { return CsvNodeType.ARRAY; }
        @Override protected int computeEnd() {
            int rowEnd = skipToRowEnd(state.csv, start);
            return skipToColumnEnd(state.csv, start, rowEnd);
        }

        /** True when the (trimmed) cell content starts with '['. */
        private boolean isArrayCell() {
            int[] t = trim(state.csv, start, endIndex());
            return t[0] < t[1] && state.csv[t[0]] == '[';
        }

        @Override public int size() {
            int[] t = trim(state.csv, start, endIndex());
            if (t[0] >= t[1]) return 0;                // empty cell ⇒ no values
            if (!isArrayCell()) return 1;              // scalar ⇒ one value
            // count top-level comma-separated elements inside brackets
            int contentStart = t[0] + 1;
            int contentEnd = t[1] - 1;
            int n = 0, i = contentStart, depth = 0;
            boolean inQuote = false;
            boolean hasAny = false;
            char[] csv = state.csv;
            while (i < contentEnd) {
                char c = csv[i];
                if (inQuote) {
                    if (c == '"') {
                        if (i + 1 < contentEnd && csv[i + 1] == '"') { i += 2; continue; }
                        inQuote = false;
                    }
                    i++;
                } else if (c == '"')  { inQuote = true; hasAny = true; i++; }
                else if (c == '[')    { depth++; hasAny = true; i++; }
                else if (c == ']')    { depth--; i++; }
                else if (c == ',' && depth == 0) { n++; i++; }
                else { if (!isWhitespace(c)) hasAny = true; i++; }
            }
            if (hasAny) n++;
            return n;
        }

        @Override public CsvNode get(int index) {
            CsvNode n = getIfExists(index);
            if (n == null) throw CsvPathException.noSuchChild(path, index, size());
            return n;
        }

        @Override public CsvNode getIfExists(int index) {
            if (index < 0) return null;
            CsvPath cp = path.chain(index);
            CsvNode cached = state.cache.get(cp);
            if (cached != null) return cached;

            int[] t = trim(state.csv, start, endIndex());
            if (t[0] >= t[1]) return null;

            if (isArrayCell()) {
                int[] span = findCommaSeparatedItem(state.csv, t[0] + 1, t[1] - 1, index);
                if (span == null) return null;
                return state.cache.computeIfAbsent(cp, k -> createValueAutoDetect(state, k, span[0], span[1]));
            }
            if (index != 0) return null;
            return state.cache.computeIfAbsent(cp, k -> createValueAutoDetect(state, k, t[0], t[1]));
        }
    }

    /* ------------------------------------------------------------------ */
    /* ARRAY (nested [..] inside a cell)                                   */
    /* ------------------------------------------------------------------ */

    static final class LazyArray extends LazyCsvNode {
        LazyArray(State s, CsvPath p, int start) { super(s, p, start); }

        @Override public CsvNodeType type() { return CsvNodeType.ARRAY; }

        @Override protected int computeEnd() {
            char[] csv = state.csv;
            int depth = 0;
            boolean inQuote = false;
            int i = start;
            while (i < csv.length) {
                char c = csv[i];
                if (inQuote) {
                    if (c == '"') {
                        if (i + 1 < csv.length && csv[i + 1] == '"') { i += 2; continue; }
                        inQuote = false;
                    }
                    i++;
                } else if (c == '"')  { inQuote = true; i++; }
                else if (c == '[')    { depth++; i++; }
                else if (c == ']')    { depth--; i++; if (depth == 0) return i; }
                else i++;
            }
            throw new CsvFormatException("Unclosed '[' at " + start);
        }

        @Override public int size() {
            int end = endIndex();
            int contentStart = start + 1, contentEnd = end - 1;
            int n = 0, i = contentStart, depth = 0;
            boolean inQuote = false;
            boolean hasAny = false;
            char[] csv = state.csv;
            while (i < contentEnd) {
                char c = csv[i];
                if (inQuote) {
                    if (c == '"') {
                        if (i + 1 < contentEnd && csv[i + 1] == '"') { i += 2; continue; }
                        inQuote = false;
                    }
                    i++;
                } else if (c == '"')  { inQuote = true; hasAny = true; i++; }
                else if (c == '[')    { depth++; hasAny = true; i++; }
                else if (c == ']')    { depth--; i++; }
                else if (c == ',' && depth == 0) { n++; i++; }
                else { if (!isWhitespace(c)) hasAny = true; i++; }
            }
            if (hasAny) n++;
            return n;
        }

        @Override public CsvNode get(int index) {
            CsvNode n = getIfExists(index);
            if (n == null) throw CsvPathException.noSuchChild(path, index, size());
            return n;
        }

        @Override public CsvNode getIfExists(int index) {
            if (index < 0) return null;
            CsvPath cp = path.chain(index);
            CsvNode cached = state.cache.get(cp);
            if (cached != null) return cached;
            int end = endIndex();
            int[] span = findCommaSeparatedItem(state.csv, start + 1, end - 1, index);
            if (span == null) return null;
            return state.cache.computeIfAbsent(cp, k -> createValueAutoDetect(state, k, span[0], span[1]));
        }
    }

    /* ------------------------------------------------------------------ */
    /* Scalars                                                             */
    /* ------------------------------------------------------------------ */

    static final class LazyString extends LazyCsvNode {
        LazyString(State s, CsvPath p, int start, int end) { super(s, p, start); this.end = end; }
        @Override public CsvNodeType type() { return CsvNodeType.STRING; }
        @Override protected int computeEnd() { return end; }
        @Override public int size() { return 0; }
        @Override public CsvNode getIfExists(int index) { return null; }
        @Override public CsvNode get(int index) { throw CsvPathException.noSuchChild(path, index, 0); }
        @Override public Text textValue() {
            char[] csv = state.csv;
            if (start < endIndex() && csv[start] == '"') {
                // strip surrounding quotes + unescape "" -> "
                StringBuilder sb = new StringBuilder(endIndex() - start - 2);
                int i = start + 1, last = endIndex() - 1;
                while (i < last) {
                    char c = csv[i];
                    if (c == '"' && i + 1 < last && csv[i + 1] == '"') { sb.append('"'); i += 2; }
                    else { sb.append(c); i++; }
                }
                return Text.of(sb);
            }
            return getDeclaration();
        }
    }

    static final class LazyNumber extends LazyCsvNode {
        LazyNumber(State s, CsvPath p, int start, int end) { super(s, p, start); this.end = end; }
        @Override public CsvNodeType type() { return CsvNodeType.NUMBER; }
        @Override protected int computeEnd() { return end; }
        @Override public int size() { return 0; }
        @Override public CsvNode getIfExists(int index) { return null; }
        @Override public CsvNode get(int index) { throw CsvPathException.noSuchChild(path, index, 0); }
        @Override public Number numberValue() { return Double.parseDouble(getDeclaration().trim().toString()); }
        @Override public Text textValue() { return getDeclaration().trim(); }
    }

    static final class LazyBoolean extends LazyCsvNode {
        LazyBoolean(State s, CsvPath p, int start, int end) { super(s, p, start); this.end = end; }
        @Override public CsvNodeType type() { return CsvNodeType.BOOLEAN; }
        @Override protected int computeEnd() { return end; }
        @Override public int size() { return 0; }
        @Override public CsvNode getIfExists(int index) { return null; }
        @Override public CsvNode get(int index) { throw CsvPathException.noSuchChild(path, index, 0); }
        @Override public boolean booleanValue() { return "true".contentEquals(getDeclaration().trim()); }
        @Override public Text textValue() { return getDeclaration().trim(); }
    }

    /** Factory that picks the right scalar/array impl for a spanned value. */
    static CsvNode createValueAutoDetect(State state, CsvPath path, int start, int end) {
        CsvNodeType type = detectType(state.csv, start, end);
        return switch (type) {
            case ARRAY   -> new LazyArray(state, path, start);
            case NUMBER  -> new LazyNumber(state, path, start, end);
            case BOOLEAN -> new LazyBoolean(state, path, start, end);
            default      -> new LazyString(state, path, start, end);
        };
    }
}