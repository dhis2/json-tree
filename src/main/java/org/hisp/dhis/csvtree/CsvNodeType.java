package org.hisp.dhis.csvtree;

public enum CsvNodeType {
  DOCUMENT,
  ROW,
  COLUMN,
  ARRAY,
  STRING,
  NUMBER,
  BOOLEAN;

  public boolean isSimple() {
    return this == STRING || this == NUMBER || this == BOOLEAN;
  }

  public boolean isContainer() {
    return !isSimple();
  }
}
