package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonNodePerfTest {

  public static void main(String[] args){
    CharSequence json = arrayOf100KTo100KAnd(10000);
    JsonNode array = JsonNode.of(json);
    double sum = 0;
    for (Text v : array.values())
      sum += v.parseDouble();
    System.out.println(sumJdkDouble_ParseDouble(json));
    System.out.println(sum);
    System.out.println(array.values().stream().mapToDouble(Text::parseDouble).sum());
  }

  @Test
  void testParseDouble10K() {
    int n = 10000;
    JsonNode node = JsonNode.of(arrayOf100KTo100KAnd(n));
    double sum = 0;
    for (int i = 0; i < n; i++) sum += 100_000d + i;
    assertEquals(sum, node.values().stream().mapToDouble(Text::parseDouble).sum());
  }

  private static double sumJdkDouble_ParseDouble(CharSequence array) {
    double sum = 0d;
    for (int i = 0; i < 10_000; i++) {
      int start = 1 + i * 9;
      int end = start+8;
      sum += Double.parseDouble(array.subSequence(start, end).toString());
    }
    return sum;
  }

  private static CharSequence arrayOf100KTo100KAnd(int n) {
    StringBuilder json = new StringBuilder(n*9+2); // 8 digits + , each + []
    json.append('[');
    for (int i = 0; i < n; i++) {
      if (i > 0) json.append(',');
      json.append(100_000 + i).append('.').append('0');
    }
    json.append(']');
    return json;
  }
}
