# User types as JSON tree

The pre-defined basic types of the virtual tree can easily be extended and mixed
with user-defined ones. Like the basic types, these are defined using
`interface`s. Properties are represented as `default` methods to build a typical
"object model".

```java
    interface JsonAddress extends JsonObject {

        default String street() {
            return getString( "street" ).string();
        }

        default Integer no() {
            return getNumber( "no" ).integer();
        }

        default JsonString city() {
            return getString( "city" );
        }
    }
```

While being convenient to define, the user has full control over conversions
and over whether properties are used on the value level or on the node level
(as `city()` is above). This also allows for logic in the property methods
(e.g. for defaults or parsing).

```java
    JsonObject obj = JsonMixed.of( """
        {
            "street": "Elm",
            "no": 11,
            "city": "Oslo"
        }""" );

    JsonAddress a = obj.as( JsonAddress.class ); // "viewing" the object as address
    a.street();        // = "Elm" (Java String)
    a.no();            // = 11 (Java Integer)
    a.city().exists(); // city() is merely the node — we can check for existence
    a.city().string(); // = "Oslo" (Java String)
```

## Nested domain models

Entire domain models can easily be created using this approach.

```java
    interface JsonUser extends JsonObject {

        default JsonAddress address() {
            return get( "address", JsonAddress.class );
        }

        default JsonList<JsonAddress> alternativeAddresses() {
            return getList( "alternativeAddresses", JsonAddress.class );
        }
    }
```

## Abstract properties

Properties don't have to be `default` methods. An abstract method declares a
property that is looked up by name: the accessor reads the JSON member whose
name matches the method name, after stripping a leading `get` or `is` prefix
and lower-casing the first remaining character.

```java
    interface JsonMembership extends JsonObject {

        String getGroup();                  // reads member "group"

        LocalDate getJoinDate();            // reads member "joinDate"

    }

    interface JsonAccount extends JsonObject {

        boolean isEnabled();                // reads member "enabled"

        String getUsername();               // reads member "username"

        List<JsonMembership> memberships(); // reads member "memberships"

    }
```

These match JSON of the shape:

```json
    {
        "enabled": true,
        "username": "TomTraubert",
        "memberships": [
            { "group": "BigTimers", "joinDate": "1973-02-05" }
        ]
    }
```

```java
    JsonAccount account = JsonMixed.of( json ).as( JsonAccount.class );
    account.isEnabled();            // true
    account.getUsername();          // "TomTraubert"
    account.memberships().size();   // 1
```

The return type determines how the member is converted: primitives and their
wrappers, `String`, `java.time` types (`LocalDate`, `LocalDateTime`,
`LocalTime`), collections, and other common JDK types are mapped
automatically. Types not covered by the built-in accessors can be added by
registering a custom `JsonAccessor`.

> [!NOTE]
> `java.time` types are parsed from ISO 8601 strings — `LocalDate` from
> `"1973-02-05"`, `LocalDateTime` from `"1973-02-05T00:00:00"`, `LocalTime`
> from `"00:00:00"`. The legacy `java.util.Date` is also accepted, but only
> via the deprecated `Date(String)` constructor, which parses legacy RFC-822
> formats. Prefer `java.time` types.

Abstract properties can be mixed freely with `default` methods and the lazily
mapped collection types (`JsonList`, `JsonMap`, …). Reach for an abstract
method when the property is just "read this member and convert it"; reach for
a `default` method when the property needs custom logic, a fallback, or a
non-trivial conversion.

A user type can extend any `JsonValue` subtype. In practice `JsonObject` is the
useful parent — it provides the generic object accessor methods (`get`,
`getString`, …) that abstract and default properties alike call into — while
`JsonValue` is the minimal base when those methods aren't needed.

## Related

- [Null, undefined and existence](intro-5-null-and-undefined.md)
- [API overview](json-api-overview.md) — most used API methods overview.
- [Performance considerations](intro-7-json-performance.md) — iterating or streaming data