package org.hisp.dhis.csvtree;

import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.Text;

public interface CsvValue {

    static CsvValue of(CharSequence csv) {
        return CsvMixed.of(csv);
    }

    CsvNodeType type();
    CsvPath path();
    CsvNode node();
    CsvNode nodeIfExists();
    boolean exists();

    Class<? extends CsvValue> asType();

    <T extends CsvValue> T as(Class<T> as);

    /* ---- convenience type-views -------------------------------------- */

    default CsvRow     asRow()     { return as(CsvRow.class); }
    default CsvColumn  asColumn()  { return as(CsvColumn.class); }
    default CsvArray   asArray()   { return as(CsvArray.class); }
    default CsvString  asString()  { return as(CsvString.class); }
    default CsvNumber  asNumber()  { return as(CsvNumber.class); }
    default CsvBoolean asBoolean() { return as(CsvBoolean.class); }

    /* ---- type tests -------------------------------------------------- */

    default boolean isUndefined() { return !exists(); }
    default boolean isRow()    { return type() == CsvNodeType.ROW; }
    default boolean isColumn() { return type() == CsvNodeType.COLUMN; }
    default boolean isArray()  { return type() == CsvNodeType.ARRAY; }
    default boolean isString() { return type() == CsvNodeType.STRING; }
    default boolean isNumber() { return type() == CsvNodeType.NUMBER; }
    default boolean isBoolean(){ return type() == CsvNodeType.BOOLEAN; }

    /* ---- child access ------------------------------------------------ */

    int size();

    <T extends CsvValue> T get(int index, Class<T> as);

    default CsvValue get(int index) { return get(index, CsvValue.class); }

    default Stream<CsvValue> children() {
        int n = size();
        return IntStream.range(0, n).mapToObj(this::get);
    }

    default boolean isEmpty() { return size() == 0; }

    /* ---- scalar accessors (mirror JsonValue) ------------------------- */

    default Text text() {
        throw new CsvTreeException("not a string node: " + type());
    }
    default String string() {
        Text t = text();
        return t == null ? null : t.toString();
    }
    default Number number() {
        throw new CsvTreeException("not a number node: " + type());
    }
    default int intValue()       { return number().intValue(); }
    default long longValue()     { return number().longValue(); }
    default double doubleValue() { return number().doubleValue(); }
    default Boolean bool() {
        throw new CsvTreeException("not a boolean node: " + type());
    }
    default boolean booleanValue() {
        Boolean b = bool();
        if (b == null) throw new CsvTreeException("undefined boolean");
        return b;
    }
    default boolean booleanValue(boolean orDefault) {
        return isUndefined() ? orDefault : booleanValue();
    }
}