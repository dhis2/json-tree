package org.hisp.dhis.csvtree;

import java.util.stream.Stream;

public interface CsvRow extends CsvValue {

  default CsvColumn column(int index) {
    return get(index, CsvColumn.class);
  }

  default Stream<CsvColumn> columns() {
    return children().map(CsvValue::asColumn);
  }
}
