package org.hisp.dhis.csvtree;

import java.util.stream.Stream;

public interface CsvColumn extends CsvValue {

  default CsvValue value(int index) {
    return get(index);
  }

  default Stream<CsvValue> values() {
    return children();
  }
}
