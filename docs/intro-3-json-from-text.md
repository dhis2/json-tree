# JSON API — building a tree from JSON

Creating a (virtual) tree from JSON is handled by the `JsonMixed` and
`JsonValue` API.

`JsonMixed` is the union type (top type) of all basic JSON types, while
`JsonValue` is the base type (bottom type) of all basic JSON types. The basic
types are `JsonBoolean`, `JsonNumber`, `JsonInteger`, `JsonString`, `JsonArray`
and `JsonObject`. An instance of one of these represents a virtual tree node;
the node's value is then accessed using the appropriate access method.

Representing JSON `null` as a type is not necessary — see
[Null, undefined and existence](intro-5-null-and-undefined) for how a node can be
undefined or a defined `null` regardless of the requested type.

## Example

```java
    JsonString str = JsonMixed.of( "\"hello\"" );
    str.isString();    // true
    str.string();      // "hello" (as Java string)
    str.type();        // JsonNodeType.STRING
```

Input must be valid JSON — this is why the string above is quoted. Same for
complex inputs:

```java
    String json = """
        {
            "key": "points",
            "value": [1,2,3]
        }
        """;
    JsonObject obj = JsonMixed.of( json );
```

## Related

- [Lazy parsing](intro-2-lazy-parsing)
- [JSON from Java](intro-4-json-from-java)
- [Null, undefined and existence](intro-5-null-and-undefined)

