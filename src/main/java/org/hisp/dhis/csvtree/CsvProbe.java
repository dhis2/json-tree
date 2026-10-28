package org.hisp.dhis.csvtree;

public interface CsvProbe {

  CsvNodeType type();
  CsvPath path();

  default boolean isRoot()   { return path().isEmpty(); }
  default boolean isRow()    { return path().isRow(); }
  default boolean isCell()   { return path().isCell(); }
  default boolean isNull()   { return type() == CsvNodeType.NULL; }
  default boolean isArray()  { return type() == CsvNodeType.ARRAY; }
  default boolean isString() { return type() == CsvNodeType.STRING; }
  default boolean isNumber() { return type() == CsvNodeType.NUMBER; }
  default boolean isBoolean(){ return type() == CsvNodeType.BOOLEAN; }

  default boolean isSimply() {
    CsvNodeType type = type();
    return type != null && type.isSimple();
  }
}
