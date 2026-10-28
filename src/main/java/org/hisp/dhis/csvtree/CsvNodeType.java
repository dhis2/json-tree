package org.hisp.dhis.csvtree;

public enum CsvNodeType {
  ARRAY,
  STRING,
  NUMBER,
  BOOLEAN,
  /**
   * In CSV it is more of an "empty" value but we call it NULL
   * to not be confused with {@link CsvNode#isEmpty}.
   */
  NULL;

  public boolean isSimple() {
    return this != ARRAY;
  }

  public boolean isContainer() {
    return this == ARRAY;
  }
}
