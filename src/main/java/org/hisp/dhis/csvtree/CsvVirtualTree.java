package org.hisp.dhis.csvtree;

import org.hisp.dhis.jsontree.Text;

final class CsvVirtualTree implements CsvMixed {

    private final CsvNode root;
    private final CsvPath path;
    private CsvNode node; // lazily resolved

    private CsvVirtualTree(CsvNode root, CsvPath path) {
        this.root = root;
        this.path = path;
    }

    static CsvMixed of(CharSequence csv) {
        return new CsvVirtualTree(CsvNode.of(csv), CsvPath.ROOT);
    }

    static CsvMixed of(CsvNode node) {
        return new CsvVirtualTree(node, CsvPath.ROOT);
    }

    /* ------------------------------------------------------------------ */
    /* CsvValue                                                            */
    /* ------------------------------------------------------------------ */

    @Override public CsvPath path() { return path; }

    @Override public CsvNodeType type() { return node().type(); }

    @Override public CsvNode node() {
        if (node == null) node = resolve(root, path);
        return node;
    }

    @Override public CsvNode nodeIfExists() {
        if (node != null) return node;
        try { return node(); } catch (CsvPathException e) { return null; }
    }

    @Override public boolean exists() { return nodeIfExists() != null; }

    @Override public Class<? extends CsvValue> asType() { return CsvMixed.class; }

    @Override public <T extends CsvValue> T as(Class<T> as) {
        if (as.isInstance(this)) return as.cast(this);
        throw new CsvTreeException("unsupported virtual type: " + as.getName());
    }

    @Override public <T extends CsvValue> T get(int index, Class<T> as) {
        CsvVirtualTree next = new CsvVirtualTree(root, path.chain(index));
        if (as.isInstance(next)) return as.cast(next);
        throw new CsvTreeException("unsupported virtual type: " + as.getName());
    }

    @Override public int size() { return node().size(); }

    /* ------------------------------------------------------------------ */
    /* Scalar overrides                                                    */
    /* ------------------------------------------------------------------ */

    @Override public Text text() { return node().textValue(); }
    @Override public Number number() { return node().numberValue(); }
    @Override public Boolean bool() {
        if (isUndefined()) return null;
        return node().booleanValue();
    }

    /* ------------------------------------------------------------------ */
    /* Helpers                                                             */
    /* ------------------------------------------------------------------ */

    private static CsvNode resolve(CsvNode root, CsvPath path) {
        return root.get(path);
    }

    @Override public boolean equals(Object o) {
        return o instanceof CsvVirtualTree other
            && other.root == root && other.path.equals(path);
    }
    @Override public int hashCode() {
        return System.identityHashCode(root) * 31 + path.hashCode();
    }
    @Override public String toString() {
        return exists() ? node().getDeclaration().toString() : "undefined";
    }
}