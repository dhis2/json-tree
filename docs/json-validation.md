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

## What you can validate

`@Validation` covers the vocabulary for structural validation from
[JSON Schema](https://json-schema.org/), adapted to annotation methods. The
constraints group by target type:

- **Any type** — `type`, `oneOfValues` / `enumeration` (enum / const),
  `required`, `dependentRequired`
- **Strings** — `minLength`, `maxLength`, `pattern`
- **Numbers** — `minimum`, `maximum`, `exclusiveMinimum`, `exclusiveMaximum`,
  `multipleOf`
- **Arrays** — `minItems`, `maxItems`, `uniqueItems`
- **Objects** — `minProperties`, `maxProperties`

Multiple attributes can be combined on one method. A few behaviours differ
from a JSON Schema document, and they're worth knowing up front:

- Some constraints (`minLength`, `minItems`, `minProperties`) implicitly
  make a property required unless `required = NO` is set explicitly.
- A constraint on a target type has no effect if the property's declared
  type is incompatible with that target (e.g. `maxLength` on a `JsonNumber`
  property).
- Method-level declarations override type-level declarations for the same
  attribute; independent attributes are merged rather than replaced.

The full attribute table, with per-attribute semantics and edge cases, is in
the [validation reference](json-validation-reference.md#overview).

## Beyond simple constraints

The basics above cover the common cases. When they don't, three features
extend the picture:

- **[Item validation](json-validation-reference.md#item-validation)** —
  apply constraints to each element of an array or member of an object,
  nested to arbitrary depth. Either through `@Items` on the container, or
  through return-type annotations like
  `List<@Validation( maxLength = 20 ) String>`.
- **[Custom validators](json-validation-reference.md#custom-validators)** —
  implement `Validation.Validator` and reference it with `@Validator` for
  format checks, cross-field rules, or anything the built-ins can't express.
- **[Dependent required](json-validation-reference.md#dependent-required)** —
  declare properties that become required based on another property's
  presence, absence, or value. Groups can express mutual exclusion
  (e.g. "email or oauth").

The [validation reference](json-validation-reference.md) covers these in
detail, along with the precedence algorithm and the RFC validations that
are deliberately not supported.

## Related

- [Validation reference](json-validation-reference.md) — full attribute table,
  precedence, item validation, custom validators, `dependentRequired`
- [User types as JSON tree](intro-6-user-types.md)
- [API reference](json-api-overview.md)
- [Input Expressions](input-expressions-reference.md) — a RegEx alternative that is simple, safe and fast