package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonNodePerfTest {

  public static void main(String[] args){
    JsonNode array = arrayOf0To(10000);
    int sum = 0;
    for (Text v : array.values())
      sum += v.parseInt();
    System.out.println(sum);
    System.out.println(array.values().stream().mapToInt(Text::parseInt).sum());
  }

  @Test
  void test() {
    runSum10K();
  }

  private static void runSum10K() {
    int n = 10000;
    JsonNode node = arrayOf0To(n);
    int sum = 0;
    for (int i = 0; i < n; i++) sum += i;
    assertEquals(sum, node.values().stream().mapToInt(Text::parseInt).sum());
  }

  private static JsonNode arrayOf0To(int n) {
    StringBuilder json = new StringBuilder(n*3);
    json.append('[');
    for (int i = 0; i < n; i++) {
      if (i > 0) json.append(',');
      json.append(i);
    }
    json.append(']');
    return JsonNode.of(json);
  }
}
