package org.hisp.dhis.csvtree;

public interface CsvBoolean extends CsvValue {

  default Boolean bool() {
    throw new CsvTreeException("not a boolean node: " + type());
  }
  default boolean booleanValue() {
    Boolean b = bool();
    if (b == null) throw new CsvTreeException("undefined boolean");
    return b;
  }
  default boolean booleanValue(boolean orDefault) {
    return isUndefined() ? orDefault : booleanValue();
  }
}
