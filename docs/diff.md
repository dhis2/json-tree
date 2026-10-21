# Comparing JSON values (diff)

When asserting that the JSON returned by a service contains the expected
information, a usual challenge is that the comparison cannot rely on
particulars such as:

- the whitespace (formatting)
- the order of properties in objects
- the order of elements in an array that is a "set"
- the presence of additional object properties
- the exact way numbers are formatted

To make such comparisons easy, the `diff` method finds the differences between
two `JsonValue` instances. The comparison can be configured using
`JsonDiff.Mode`.

```java
    JsonValue expected = JsonValue.of( "[1,2,3]" );
    JsonValue actual   = JsonValue.of( "[1,3,2]" );

    JsonDiff diff1 = expected.diff( actual );                     // is different
    JsonDiff diff2 = expected.diff( actual, JsonDiff.Mode.LENIENT ); // same
```

## Per-property opt-in

To adjust individual properties of `JsonObject` subtypes, annotate the
`default` property method with `@JsonDiff.AnyOrder` and
`@JsonDiff.AnyAdditional` to opt into a lenient handling.

```java
    interface MyObject extends JsonObject {

        @JsonDiff.AnyOrder
        default JsonArray tags() {
            return getArray( "tags" );
        }
    }
```

The used `Mode` is then overridden for the annotated property:

```java
    MyObject expected = JsonValue.of( """
        {"tags": ["hello", "intro"]}""" ).as( MyObject.class );
    MyObject actual = JsonValue.of( """
        {"tags": ["intro", "hello"]}""" ).as( MyObject.class );

    JsonDiff diff1 = expected.diff( actual ); // same
```

## Related

- [User types as JSON tree](user-types.md)
- [Validation](validation.md)