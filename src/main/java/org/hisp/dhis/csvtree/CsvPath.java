package org.hisp.dhis.csvtree;

import java.util.Arrays;

/** The path of a node in a CSV tree: {@code [rowIdx, columnIdx, valueIdx, arrIdx, arrIdx, ...]}. */
public final class CsvPath {

  public static final CsvPath ROOT = new CsvPath(new int[0]);

  private final int[] indexes;

  private CsvPath(int[] indexes) {
    this.indexes = indexes;
  }

  public static CsvPath of(int... indexes) {
    return indexes.length == 0 ? ROOT : new CsvPath(indexes.clone());
  }

  public static CsvPath row(int rowIndex) {
    return new CsvPath(new int[] {rowIndex});
  }

  public int length() {
    return indexes.length;
  }

  public boolean isEmpty() {
    return indexes.length == 0;
  }

  public int get(int i) {
    return indexes[i];
  }

  public CsvPath chain(int index) {
    int[] next = Arrays.copyOf(indexes, indexes.length + 1);
    next[indexes.length] = index;
    return new CsvPath(next);
  }

  public CsvPath concat(CsvPath other) {
    if (other.isEmpty()) return this;
    if (this.isEmpty()) return other;
    int[] next = new int[indexes.length + other.indexes.length];
    System.arraycopy(indexes, 0, next, 0, indexes.length);
    System.arraycopy(other.indexes, 0, next, indexes.length, other.indexes.length);
    return new CsvPath(next);
  }

  public CsvPath parentPath() {
    return indexes.length <= 1 ? ROOT : new CsvPath(Arrays.copyOf(indexes, indexes.length - 1));
  }

  public int segment() {
    return indexes[indexes.length - 1];
  }

  @Override
  public boolean equals(Object o) {
    return o instanceof CsvPath p && Arrays.equals(indexes, p.indexes);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(indexes);
  }

  @Override
  public String toString() {
    return Arrays.toString(indexes);
  }
}
