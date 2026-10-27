package org.hisp.dhis.csvtree;

import java.util.stream.Stream;

public interface CsvArray extends CsvValue {

  default CsvValue element(int index) {
    return get(index);
  }

  default Stream<CsvValue> elements() {
    return children();
  }
}
