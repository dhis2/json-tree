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

The benefit over using `record`s or Java `class`es JSON is mapped to is 
that no CPU or memory has to be spent on mapping a JSON text input 
while also allowing to work with the input as if it is a domain model.
The flexibility even allows for alternate form without much issue.
For example, a header that is either a list of field objects or 
an object representing said fields can both be supported with ease. 

## Related

- [Validation](validation.md)
- [Java API](java-api.md)