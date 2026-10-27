package org.hisp.dhis.csvtree;

public class CsvPathException extends CsvTreeException {
  public CsvPathException(String message) {
    super(message);
  }

  public static CsvPathException noSuchChild(CsvPath path, int index, int size) {
    return new CsvPathException(
        "No child at index " + index + " in " + path + " (size " + size + ")");
  }
}
