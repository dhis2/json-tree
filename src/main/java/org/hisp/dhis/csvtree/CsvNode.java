package org.hisp.dhis.csvtree;

import org.hisp.dhis.jsontree.Text;

public interface CsvNode extends CsvProbe {

  /* factories */
  static CsvNode of(CharSequence csv) {
    return CsvTree.parse(csv);
  }

  CsvNode getRoot();

  CsvNode getParent();

  Text getDeclaration();

  int startIndex();

  int endIndex();

  default CsvNode get(CsvPath path) {
    return null; //TODO
  }

  /** The K-th child of this node (row/column/value/element depending on type). */
  CsvNode get(int index) throws CsvPathException;

  CsvNode getIfExists(int index);

  int size();

  default boolean isEmpty() {
    return size() == 0;
  }

  /* primitive accessors – throw unless type matches */
  default Text textValue() {
    throw new CsvTreeException("not a text node: " + type());
  }

  default boolean booleanValue() {
    throw new CsvTreeException("not a boolean node: " + type());
  }

  default Number numberValue() {
    throw new CsvTreeException("not a number node: " + type());
  }

  default int intValue() {
    return numberValue().intValue();
  }

  default long longValue() {
    return numberValue().longValue();
  }

  default double doubleValue() {
    return numberValue().doubleValue();
  }
}
