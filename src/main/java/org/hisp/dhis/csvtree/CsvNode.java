package org.hisp.dhis.csvtree;

import org.hisp.dhis.jsontree.Text;

public interface CsvNode {

  /* factories */
  static CsvNode of(CharSequence csv) {
    return CsvTree.parse(csv);
  }

  CsvNodeType type();

  CsvPath path();

  CsvNode getRoot();

  CsvNode getParent();

  Text getDeclaration();

  int startIndex();

  int endIndex();

  /** The K-th child of this node (row/column/value/element depending on type). */
  CsvNode child(int index) throws CsvPathException;

  CsvNode childIfExists(int index);

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
