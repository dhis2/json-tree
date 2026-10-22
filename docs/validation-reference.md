# Validation reference

> [!TIP]
> This page assumes familiarity with the basics in [Validation](validation.md).
> Later sections build upon earlier ones, so reading top-to-bottom is recommended.

## Overview

Most of the validations specified by the [JSON schema](https://json-schema.org/)
are supported. However, since annotations are used to add validation to a
property method, some of the validations work slightly differently than
specified so that each targets the annotated method's value. For example
`required` is specified on each required property instead of specifying a
list of required properties on their parent. This is also to avoid string
name references that are not safe to refactor.

Validations include:

| Target   | JSON schema         | `@Validation` attribute            | Description                                                                                                                               |
|----------|---------------------|------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| (any)    | `type`              | `type()`                           | annotated property must be one of the JSON node types (with `INTEGER` being a distinct type)                                              |
| (any)    | `enum` / `const`    | `oneOfValues()` + `enumeration()`  | annotated property's value must be one of a given set of JSON values or the given `enum` constant names                                   |
| (any)    | `required`          | `required()` or use `@Required`    | annotated property must be present (unconditionally), meaning defined and non-`null`                                                      |
| (any)    | `dependentRequired` | `dependentRequired()`              | annotated property must be present (conditionally); conditions are modelled using named groups and roles in the group (details below)     |
| `STRING` | `minLength`         | `minLength()`                      | annotated text property must have a minimum length; implicitly makes it required unless `required` explicitly states `NO`                 |
| `STRING` | `maxLength`         | `maxLength()`                      | annotated text property must not be longer than the maximum length (inclusive)                                                            |
| `STRING` | `pattern`           | `pattern()`                        | annotated text property must match the given [`InputExpression`](input-expressions.md) pattern                                            |
| `NUMBER` | `minimum`           | `minimum()`                        | annotated number property must be larger than or equal to the minimum value                                                               |
| `NUMBER` | `maximum`           | `maximum()`                        | annotated number property must be less than or equal to the maximum value                                                                 |
| `NUMBER` | `exclusiveMinimum`  | `exclusiveMinimum()`               | annotated number property must be larger than the minimum value                                                                           |
| `NUMBER` | `exclusiveMaximum`  | `exclusiveMaximum()`               | annotated number property must be less than the maximum value                                                                             |
| `NUMBER` | `multipleOf`        | `multipleOf()`                     | annotated number property must be divisible by the given number without remainder                                                         |
| `ARRAY`  | `minItems`          | `minItems()`                       | annotated array property must have at least the number of elements; implicitly makes it required unless `required` explicitly states `NO` |
| `ARRAY`  | `maxItems`          | `maxItems()`                       | annotated array property must have at most the number of elements                                                                         |
| `ARRAY`  | `uniqueItems`       | `uniqueItems()`                    | annotated array property must not have any duplicates (same "normalized" JSON)                                                            |
| `OBJECT` | `minProperties`     | `minProperties()`                  | annotated object property must have at least the number of members; implicitly makes it required unless `required` explicitly states `NO` |
| `OBJECT` | `maxProperties`     | `maxProperties()`                  | annotated object property must have at most the number of members                                                                         |

> [!NOTE]
> If a validation for a target type is used but that type does not apply — based
> on the return type analysis or the type(s) declared via `type()` — the
> validation has no effect, as it does not apply to any valid value for the
> property.

## Usage

The primary way to add validation to a property is to annotate the method with
`@Validation` and use one or more of the annotation attributes:

```java
    interface User extends JsonObject {

      @Validation( pattern = "[a-z]+", minLength = 8, maxLength = 20 )
      default String username() {
        return getString( "username" ).string();
      }
    }
```

Alternatively, meta-annotations can be used. These are annotations that
themselves are annotated with `@Validation`. `@Required` is an example of such
a meta-annotation; users can easily define their own:

```java
    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    @Validation( required = YesNo.NO )
    public @interface Optional {}
```

## Implicitly required

`minLength`, `minItems` and `minProperties` all make a property *implicitly
required*: it is required as long as its `required` evaluates to `AUTO` (the
default). With an `@Optional` annotation as above the user can explicitly
declare it non-required.

All properties that use primitive return types are also considered *implicitly
required*, but they might use a default so they are actually not required:

```java
    interface User extends JsonObject {

      @Optional
      default boolean expired() {
        return getBoolean( "expired" ).booleanValue( false );
      }
    }
```

## Precedence

`@Validation` restrictions can be declared in several places.

Validations that are always given for a specific Java type can be annotated on
that type:

```java
    @Validation( type = STRING, pattern = "[a-zA-Z0-9]{11}" )
    record ID( String id ) {}

    interface User extends JsonObject {

      default ID getId() {
        return getString( "id" ).parsed( ID::new );
      }
    }
```

Or the annotation is placed on the property method or its return type:

```java
    interface User extends JsonObject {

      @Required
      default List<@Validation( maxLength = 20 ) String> names() {
        return getArray( "names" ).stringValues();
      }
    }
```

Here `names` is required and each name has a `maxLength` of 20.

> [!IMPORTANT]
> When multiple annotations affect a property, the restrictions declared on the
> method and return type override the restrictions declared on Java types.
> Independent restrictions are merged. For example, using the `ID` type limits
> `type` to `STRING` and requires a certain `pattern` to match. If a property
> using `ID` is now annotated `@Required`, the `required=YES` restriction is
> added on top of the restriction from the `ID` type. If, on the other hand,
> `ID` also included `required=YES` and the method gets annotated `@Optional`,
> the `required=NO` from `@Optional` overrides the `required=YES` declared for
> `ID`.

## Item validation

For complex JSON types `ARRAY` and `OBJECT` the items can be restricted
independently from the container. Either by annotating the container with
`@Items` or by using return type annotations:

```java
    interface User extends JsonObject {

      @Items( @Validation( maxLength = 20 ) )
      default List<String> names() { return getArray( "names" ).stringValues(); }
    }
```

is identical to

```java
    interface User extends JsonObject {

      default List<@Validation( maxLength = 20 ) String> names() {
        return getArray( "names" ).stringValues();
      }
    }
```

The benefit of the latter is that it can add validations at multiple levels:

```java
    interface User extends JsonObject {

      default List<@Validation( minItems = 1 )
                   List<@Validation( uniqueItems = YesNo.YES ) Point>> points() {
        // ...
      }
    }
```

`@Items` can also be used to create dedicated complex types:

```java
    @Validation( type = ARRAY )
    @Items( @Validation( type = INTEGER ) )
    interface IntList extends JsonList<JsonInteger> {}
```

## Custom validators

If no combination of the provided validations is sufficient, users can declare
their own using `@Validator`. The annotation refers to a `record` class that
implements the `Validation.Validator` interface:

```java
    record DateValidator() implements Validation.Validator {

      @Override
      public void validate( JsonMixed value, Consumer<Error> addError ) {
        if ( !value.isString() ) return;
        try {
          LocalDate.parse( value.string() );
        } catch ( Exception ex ) {
          addError.accept( Error.of( Rule.CUSTOM, value,
              "not a valid date: %s", value.string() ) );
        }
      }
    }
```

> [!IMPORTANT]
> Custom validators should always assume they are called with any type of JSON
> node, depending on what was present in the actual input. It is good practice
> to **ignore** types that are not suited for validation according to the
> value's semantic. Any type mismatches should be caught by using
> `@Validation( type = ... )`.

To use a custom validator, annotate the property with `@Validator`:

```java
    interface User extends JsonObject {

      @Validator( DateValidator.class )
      default String dateOfBirth() {
        return getString( "dateOfBirth" ).string();
      }
    }
```

Users can equally define their own validator meta-annotations to reuse the
same validation without linking to the specific validator at every usage site:

```java
    @Target( ElementType.METHOD )
    @Retention( RetentionPolicy.RUNTIME )
    @Validator( DateValidator.class )
    public @interface Date {}

    interface User extends JsonObject {

      @Date
      default String dateOfBirth() {
        return getString( "dateOfBirth" ).string();
      }
    }
```

## Dependent required

`dependentRequired` declares properties as required only when a specific
condition is met.

To relate object properties with each other they are given the same *group
name* in the list of `dependentRequired` conditions. These names are
user-defined and must be unique within the context of the annotated class, to
group properties added to the same group into a dependent-required relation. A
property can be in multiple groups.

In the simplest form a group declares a set of *codependent* properties: if
one of them is present, all of them must be present.

```java
    interface User extends JsonObject {

      @Validation( dependentRequired = ".name" )
      default String firstName() { return getString( "firstName" ).string(); }

      @Validation( dependentRequired = ".name" )
      default String lastName() { return getString( "lastName" ).string(); }
    }
```

Here both properties use group name `.name`. Neither is marked with a trigger
condition — see below.

In more advanced groups the member properties are split into *triggers*
(properties with a trigger condition) and *dependent* properties (properties
with the same group but no trigger condition).

### Present trigger

A common case: make the dependent properties of a group required when a
certain property is present. That property adds the present trigger condition
`[=*]` (think: has any value) to the group.

For example, when the first name is given, the last name must also be given:

```java
    interface User extends JsonObject {

      @Validation( dependentRequired = ".name[=*]" )
      default String firstName() { return getString( "firstName" ).string(); }

      @Validation( dependentRequired = ".name" )
      default String lastName() { return getString( "lastName" ).string(); }
    }
```

### Absent trigger

The opposite: trigger a group when a property is absent (undefined). Such a
trigger is marked with the absent trigger condition `[=?]` suffix.

For example, when no `email` is given, `oauth` is required:

```java
    interface User extends JsonObject {

      @Validation( dependentRequired = ".credentials[=?]" )
      default String email() { return getString( "email" ).string(); }

      @Validation( dependentRequired = ".credentials" )
      default String oauth() { return getString( "oauth" ).string(); }
    }
```

### Equals trigger

Triggers can also be based on the current string value of a property:

```java
    interface JsonPatch extends JsonObject {

      @Validation( dependentRequired = { ".add[=add]", ".move[=move]" } )
      default String operation() { return getString( "op" ).string(); }

      @Validation( dependentRequired = ".add" )
      default JsonMixed value() { return get( "value", JsonMixed.class ); }

      @Validation( dependentRequired = ".move" )
      default String from() { return getString( "from" ).string(); }
    }
```

An equals-comparing trigger uses the equals trigger condition `[={value}]`
after the group name, followed by the value that triggers the group. So
`value` is required when `op` equals `add`, and `from` is required when `op`
equals `move`.

### Combining triggers and mutual exclusion

A group may have more than one trigger property. In that case they all have
to be satisfied to trigger. It is possible to mix present, absent and equals
triggers in a group.

Finally, a dependent-required property can be marked as *mutually exclusive*
using the `*` suffix after the group name. For example, to require either an
email or an oauth login:

```java
    interface User extends JsonObject {

      @Validation( dependentRequired = "login*" )
      default String email() { return getString( "email" ).string(); }

      @Validation( dependentRequired = "login*" )
      default String oauth() { return getString( "oauth" ).string(); }
    }
```

The *mutual exclusion* marker can also be combined with triggers to only
check exclusivity when the group is triggered.

## Unsupported RFC validations

> [!NOTE]
> This section lists JSON schema validations that were considered but are
> currently unsupported. They might be added in a later release, but there are
> no plans at the moment. It is mainly kept to preserve the reasoning about
> how the JSON schema concept would fit into the Java annotation based
> validation.

### Array `items` + `additionalItems`

The `items` constraint states for each element of an array what is expected
(in terms of JSON schema constraints). This allows very precise control over
how an array should look — down to each single element. When mapping JSON to
a Java model, it is unlikely such control is needed; a typed language like
Java would model this as an object with specific properties rather than an
array with specific elements.

If it were to be included in `@Validation`, we would add

```java
    Class<?>[] items() default {};
```

Each `Class<?>` reference would correspond to an array element and would be
expected to be annotated with `@Validation`. This is the only way to use
annotations "recursively" (for the items) by referencing an annotated type.

If `items` can be specified, then `additionalItems` also becomes useful, to
state whether other items (beyond the first n elements covered by `items`)
are allowed:

```java
    YesNo additionalItems() default YesNo.AUTO;
```

### Array `contains` + `minContains` + `maxContains`

The `contains` constraint ensures that the array contains at least one element
matching the conditions given as JSON schema. Again, because annotations
cannot be recursive, the way to declare the `@Validation` is by referencing a
`Class` that has the annotation in question:

```java
    Class<?> contains() default Void.class;
```

If `contains` is added this way, `minContains` and `maxContains` should also
be added, to allow setting a range for how often the element with the
conditions should occur:

```java
    int minContains() default -1;
    int maxContains() default -1;
```

### Object `additionalProperties`

The `additionalProperties` constraint that might be useful in the context of
JSON in Java is the variant to allow (`true`) or disallow (`false`) properties
not contained in the JSON schema. Currently, when mapping JSON to Java,
additional properties are generally ignored. Giving control to disallow it
does not add much value, but it would require linking validation with the Java
model more tightly. If it were to be added in `@Validation`, it would take this
form:

```java
    YesNo additionalProperties() default YesNo.AUTO;
```

## Related

- [Validation](validation.md) — basic usage, `@Required`, `isA` / `asA`
- [API reference](api-reference.md)