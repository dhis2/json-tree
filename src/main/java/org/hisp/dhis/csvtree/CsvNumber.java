package org.hisp.dhis.csvtree;

public interface CsvNumber extends CsvValue {

  default Number number() {
    throw new CsvTreeException("not a number node: " + type());
  }
  default int intValue()       { return number().intValue(); }
  default long longValue()     { return number().longValue(); }
  default double doubleValue() { return number().doubleValue(); }

}
