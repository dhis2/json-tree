# Null, undefined and existence

Because a virtual tree lets any node be the type you *want* rather than the type
actually present, the value for a requested type can be in one of several
states: it may be a real value, it may be a defined `null`, or it may not exist
at all in the underlying tree.

The `JsonValue` and `JsonMixed` API will first resolve against the actual tree
represented as `JsonNode` when a terminal operation occurs. If the type of the
terminal operation is a general mismatch for the node found an exception is
thrown at that point but if the operation has a reasonable interpretation exists
for the node found the corresponding value is returned. 

This behaviour allows to navigate and process trees without constantly guarding
against or distinguishing undefined vs defined cases. Instead, the processing 
code can be written as if values are present, and it will translate to the
expected output unless there is a general mismatch with the JSON input.

## JSON null

```java
    JsonString s = JsonMixed.of( "null" );
    s.exists();      // true, it is a NULL node in the actual tree
    s.isUndefined(); // true
    s.isNull();      // true
    s.isString();    // false
    s.type();        // JsonNodeType.NULL
    s.string();      // null (Java null)
```

## Undefined 

Is a node that is absent in the actual tree or a node defined JSON `null`.

```java
    JsonString s = JsonMixed.of( "{}" ).getString( "x" ); // the member "x" as string
    s.exists();      // false
    s.isUndefined(); // true
    s.type();        // null (Java null)
    s.string();      // null (string() in particular is forgiving)
```

## Related

- [JSON API](json-api.md)
- [Lazy parsing](lazy-parsing.md)