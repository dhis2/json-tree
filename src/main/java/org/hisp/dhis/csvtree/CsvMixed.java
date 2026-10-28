package org.hisp.dhis.csvtree;

public interface CsvMixed
    extends CsvValue, CsvArray, CsvString, CsvNumber, CsvBoolean {

  static CsvMixed of(CharSequence csv) {
    return CsvVirtualTree.of(csv);
  }

  static CsvMixed of(CsvNode node) {
    return CsvVirtualTree.of(node);
  }
}
