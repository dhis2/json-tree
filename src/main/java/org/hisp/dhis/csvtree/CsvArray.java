package org.hisp.dhis.csvtree;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A CSV document is an array of rows which are an array of columns which are mostly simple values
 * but could again be arrays (of arrays...). Or in other words CSV is nested arrays with 3 simple
 * types at the leaves: strings, numbers and booleans.
 */
public interface CsvArray extends CsvValue {

  default boolean isEmpty() { return size() == 0; }

  int size();

  <T extends CsvValue> T get(int index, Class<T> as);

  default CsvMixed get(int index) { return get(index, CsvMixed.class); }

  default Stream<CsvMixed> elements() {
    CsvNode node = nodeIfExists();
    if (node == null) return Stream.empty();
    return null; //TODO
  }
}
