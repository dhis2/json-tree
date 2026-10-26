# Validation of a JSON tree

Since a virtual tree describes the "wanted" structure, a common use case is to
check the actual input against this target. Objects can be validated directly:

```java
    JsonObject obj = JsonMixed.of( """
        {
            "city": "Oslo",
            "no": 42
        }""" );
    obj.validate( JsonAddress.class ); // no exception when contract matches
```

## Annotated validation

The basic types all include validation for the JSON node type — a string
property cannot have an actual value that is a JSON number, and so on. To
extend the requirements, add `@Validation` (or a meta-annotation like
`@Required`) to the property methods:

```java
    interface JsonAddress extends JsonObject {

        @Required
        @Validation( maxLength = 50 )
        default String street() {
            return getString( "street" ).string();
        }

        @Validation( required = YesNo.YES, minimum = 1 )
        default Integer no() {
            return getNumber( "no" ).integer();
        }

        @Validation( maxLength = 50 )
        default JsonString city() {
            return getString( "city" );
        }
    }
```

With this declaration, a JSON input that lacks `street` will throw when
`obj.validate( JsonAddress.class )` is called.

The provided validations follow the JSON schema validation specification.

## Custom meta annotations

Users can create their own meta-annotations by annotating the annotation type
with `@Validation`:

```java
    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    @Validation( minimum = 1 )
    public @interface Positive {}
```

## `isA` and `asA`

To check conformity with a given schema type, `JsonObject::isA` can be used.
`JsonObject::asA` is the same as `JsonValue::as` with a validation check before
the "cast".

```java
    JsonObject obj = JsonMixed.of( """
        {
            "city": "Oslo",
            "no": 0,
            "street": "Elm"
        }""" );

    obj.isA( JsonAddress.class );                 // false, no < 1
    JsonAddress a = obj.asA( JsonAddress.class ); // throws exception, no < 1
```

Both `isA` and `asA` can also be limited to a given set of validation `Rule`
types.

## Related

- [Validation reference](json-validation-reference.md) — full attribute table,
  precedence, item validation, custom validators, `dependentRequired`
- [User types as JSON tree](intro-6-user-types.md)
- [API reference](json-api-overview.md)
- [Input Expressions](input-expressions-reference.md) — a RegEx alternative that is simple, safe and fast