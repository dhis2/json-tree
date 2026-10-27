package org.hisp.dhis.csvtree;

public class CsvTreeException extends RuntimeException {
  public CsvTreeException(String message) {
    super(message);
  }

  public CsvTreeException(String message, Throwable cause) {
    super(message, cause);
  }
}
