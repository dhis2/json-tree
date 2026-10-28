package org.hisp.dhis.csvtree;

import org.hisp.dhis.jsontree.Text;

import java.util.Objects;

/** The path of a node in a CSV tree: {@code [rowIdx, columnIdx, valueIdx, arrIdx, arrIdx, ...]}. */
public record CsvPath(CsvPath parent, int index) {

  public static final CsvPath ROOT = new CsvPath(null, -1);

  public static CsvPath of(CharSequence path) {
    if (path == null || path.isEmpty()) return ROOT;
    Text p = Text.of(path);
    CsvPath res = null;
    int len = p.length();
    int s = 0;
    while (s < len) {
      int e = p.indexOf('-', s);
      if (e < 0) e = len;
      res = new CsvPath(res, p.subSequence(s, e).parseInt());
      s = e+1;
    }
    return res;
  }

  public static CsvPath of(int... indexes) {
    if (indexes.length == 0) return ROOT;
    return ROOT.concat(indexes);
  }

  public CsvPath {
    if (index == -1 && parent != null)
      throw new CsvPathException("Root index -1 must not have a parent, but was: "+parent);
    if (index < -1) throw new CsvPathException("Index must be non-negative, but was: " + index);
  }


  public int length() {
    if (isEmpty()) return 0;
    return parent == null ? 1 : parent.length() + 1;
  }

  public boolean isEmpty() {
    return parent == null && index == -1;
  }

  public boolean isRow() {
    return parent == null && index >= 0;
  }

  public boolean isCell() {
    return parent != null && parent().isRow();
  }

  public CsvPath chain(int index) {
      return new CsvPath(this, index);
  }

  public CsvPath concat(CsvPath subPath) {
    if (isEmpty()) return subPath;
    if (subPath.isEmpty()) return this;
    if (subPath.isRow()) return chain(subPath.index);
    CsvPath res = this;
    for (int i : segments()) res = new CsvPath(res, i);
    return res;
  }

  public CsvPath concat(int... indexes) {
    CsvPath res = isEmpty() ? null : this;
    for (int i = 0; i < indexes.length; i++) res = new CsvPath(res, indexes[i]);
    return res;
  }

  private int[] segments() {
    if (isEmpty()) return new int[0];
    if (isRow()) return new int[index];
    int n = length();
    CsvPath p = this;
    int[] res = new int[n];
    for (int i = n - 1; i >= 0; i--) {
      res[i] = p.index;
      p = p.parent;
    }
    return res;
  }

  public CsvPath parentPath() {
    return parent;
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof CsvPath other && index == other.index && Objects.equals(parent, other.parent);
  }

  @Override
  public int hashCode() {
    return parent == null ? index : parent.hashCode() ^ index;
  }

  @Override
  public String toString() {
    if (isEmpty()) return "";
    return parent == null ? index + "" : parent + "-" + index;
  }
}
