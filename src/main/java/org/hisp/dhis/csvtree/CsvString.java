package org.hisp.dhis.csvtree;

import org.hisp.dhis.jsontree.Text;

public interface CsvString extends CsvValue {

  default Text text() {
    throw new CsvTreeException("not a string node: " + type());
  }
  default String string() {
    Text t = text();
    return t == null ? null : t.toString();
  }
}
