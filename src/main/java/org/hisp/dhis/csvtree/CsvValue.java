package org.hisp.dhis.csvtree;

public interface CsvValue extends CsvProbe {

    static CsvValue of(CharSequence csv) {
        return CsvMixed.of(csv);
    }

    CsvNode node();
    CsvNode nodeIfExists();


    Class<? extends CsvValue> asType();

    <T extends CsvValue> T as(Class<T> as);

    /* ---- convenience type-views -------------------------------------- */

    default CsvArray   asArray()   { return as(CsvArray.class); }
    default CsvString  asString()  { return as(CsvString.class); }
    default CsvNumber  asNumber()  { return as(CsvNumber.class); }
    default CsvBoolean asBoolean() { return as(CsvBoolean.class); }

    /* ---- tests -------------------------------------------------- */

    boolean exists();
    default boolean isUndefined() { return !exists() || isNull(); }

 }