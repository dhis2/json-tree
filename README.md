# json-tree

[![main](https://github.com/dhis2/json-tree/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/dhis2/json-tree/actions/workflows/main.yml)

A lazily parsed JSON virtual tree library.

Virtually here means it represents the "wanted" or "expected" tree structure.
On leaf value access it is checked against the actual tree structure underneath.
As a result tree navigation is painless and checks only have to be made for 
and when values are resolved (extracted) from the actual tree.

Lazily parsed means only the parts of the underlying tree that are used during
navigation will be parsed into nodes which remember offsets ("pointers") into 
the original JSON input.
Untouched parts of a JSON tree are skipped over until they are used.
This makes for a fast and memory efficient implementation that is convenient 
to query and extend with user types that can handle inputs in MB range without
problem. 

## Java to JSON Tree
Creating a (virtual) tree from Java types is handled by the `Json` API.

```java
JsonString s  = Json.of("hello"); // note: not quoted
JsonBoolean b = Json.of(true);
JsonInteger i = Json.of(42);
JsonNumber d  = Json.of(42.01d);
JsonArray a   = Json.array(Json::of, List.of(1,2,3));
JsonObject o  = Json.object(Json::of, Map.of("a", 4, "b", 2));
```

Arrays and objects can also be composed in various ways using a builder API.
```java
JsonArray a = Json.array(arr -> arr.addNumber(1).addString("yes"));
a.toJson();   // [1,"yes"]

JsonObject o = Json.object(obj -> obj.addNumber("x", 1).addNumber("y", 5));
o.toJson();   // {"x":1,"y":5}

JsonObject o2 = Json.object(obj -> obj.addArray("points", 
        arr -> arr.addNumbers(IntStream.range(0,10))));
o2.toJson();  // {"points":[1,2,3,4,5,6,7,8,9]}
```

## JSON to JSON Tree
Creating a (virtual) tree from JSON is handled by the `JsonMixed` and 
`JsonValue` API.

`JsonMixed` is the union type (top type) of all basic JSON types, 
while `JsonValue` is the base type (bottom type) of all basic JSON types.

The basic types are `JsonBoolean`, `JsonNumber`, `JsonInteger`, `JsonString`, 
`JsonArray` and `JsonObject`. 
An instance of these represents a virtual tree node. 
Their values are then accessed using the appropriate access method on the node.

Representing JSON `null` as type is not necessary since in a virtual tree each
node can be of the type that is wanted, not the type actually present.
Hence, the actual value for any type can be undefined or defined `null`.

```java
JsonString s = JsonMixed.of("null");
s.exists();      // true, it is a NULL node in the actual tree
s.isUndefined(); // true
s.isNull();      // true
s.isString();    // false
s.type();        // JsonNodeType.NULL
s.string();      // throws exception
```
Whereas nodes can actually not exist. For example:
```java
JsonString s = JsonMixed.of("{}").getString("x"); // the member "x" as string
s.exists();      // false
s.isUndefined(); // true
s.type();        // null (Java null)        
s.string();      // throws exception
```

When creating a virtual tree using `JsonMixed.of` or `JsonValue.of` the
input must be valid JSON. This means a string must be quoted.

```java
JsonString s = JsonMixed.of("\"null\"");
s.isString();    // true
s.string();      // "null" (as Java string)
s.type();        // JsonNodeType.STRING
```

Equally, to create a virtual tree for a complex JSON input:
```java
String json = """
    {   
        "key": "points", 
        "value": [1,2,3] 
    }
    """;
JsonObject o = JsonMixed.of(json);
```

## User Types as JSON Tree
The pre-defined basic types of the virtual tree can easily be extended and mixed
with user defined ones. Like the basic types these are defined using `interface`s.
Properties are represented as `default` methods to build a typical "object model".

```java
interface JsonAddress extends JsonObject {
    default String street() {
        return getString("street").string();
    }
    default Integer no() {
        return getNumber("no").integer();
    }
    default JsonString city() {
        return getString("city");
    }
}
```
While being convenient to define the user has full control over conversions and 
weather or not properties are used on the value level or the node level (city).
This also allows for logic in the property methods (e.g. for default or parsing).

```java
JsonObject obj = JsonMixed.of("""
    {
        "street": "Elm", 
        "no": 11, 
        "city": "Oslo"
    }""");
JsonAddress a = obj.as(JsonAddress.class); // "viewing" the object as address
a.street();        // = "Elm" (Java String)
a.no();            // = 11 (Java Integer)
a.city().exits();  // as city() is merely the node we can check for existence
a.city().string(); // = "Oslo" (Java String)
```

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

## Validation of a JSON Tree
Since a virtual tree describes the "wanted" structure a common use case is to
check the actual input against this target. For that objects can be validated.

```java
JsonObject obj = JsonMixed.of("""
    {
        "city": "Oslo", 
        "no": 42
    }""");
obj.validate(JsonAddress.class); // node types match, no exception
```

The basic types all include validation for the JSON node type. This means a
string property cannot have an actual value that is a JSON number and so on.
To extend the requirements additional validations can be added using 
annotations.

```java
interface JsonAddress extends JsonObject {
    @Required
    @Validation( maxLength = 50 )
    default String street() {
        return getString( "street" ).string();
    }
    @Validation( required = YesNo.YES, minimum = 1)
    default Integer no() {
        return getNumber( "no" ).integer();
    }
    @Validation( maxLength = 50 )
    default JsonString city() {
        return getString( "city" );
    }
}
```
With the improved `JsonAddress` declaration a JSON input that lacks `street`
would now result in an error and throw an exception when calling 
`obj.validate(JsonAdress.class)`.

Validation is either given by annotating with `@Validation` or using meta
annotation like `@Required`. The provided validation follow the JSON schema
validation specification.

Users can simply create their own meta annotations by annotating the annotation 
type with `@Validation`, for example:

```java
@Target( ElementType.METHOD )
@Retention( RetentionPolicy.RUNTIME )
@Validation( mininum = 1 )
public @interface Positive {}
```

To check conformity with a given schema type `JsonObject::isA` can be used. 
The `JsonObject::asA` is same as `JsonValue::as` with a validation check before
the "cast".
```java
JsonObject obj = JsonMixed.of("""
    {
        "city": "Oslo", 
        "no": 0, 
        "street": "Elm"
    }""");
obj.isA(JsonAddress.class);                 // false, no < 1
JsonAddress a = obj.asA(JsonAddress.class); // throws exception, no < 1
```
Both `isA` and `asA` can also be limited to a given set of validation `Rule`
types.


## Comparing JSON values (diff)
When asserting that the JSON returned by a service contains
the expected information a usual challenge is that the
comparison cannot rely on a specifics such as

* the whitespace (formatting)
* the order of properties in objects
* the order of elements in an array that is a "set"
* the presence of additional object properties
* the exact way numbers are formatted

To make such comparisons easy the `diff` method can be used
to find the differences between two `JsonValue` instances.
When computing the difference the comparison can be configured
using the `JsonDiff.Mode`.

```java
JsonValue expected = JsonValue.of( "[1,2,3]" );
JsonValue actual = JsonValue.of( "[1,3,2]" );

JsonDiff diff1 = expected.diff( actual ); // is different
JsonDiff diff2 = expected.diff( actual, JsonDiff.Mode.LENIENT); // same
```

To adjust individual properties of `JsonObject` subtypes the `default` 
methods of its properties can be annotated with `@AnyOrder` and 
`@AnyAdditional` to opt into a lenient handling.

```java
interface MyObject extends JsonObject {

    @JsonDiff.AnyOrder
    default JsonArray tags() {
        return getArray( "tags" );
    }
}
```
The used `Mode` is now overridden for the annotated property:
```java
MyObject expected = JsonValue.of( """
    {"tags": ["hello", "intro"]}""" ).as( MyObject.clas );
MyObject actual = JsonValue.of( """
    {"tags": ["intro", "hello"]}""" ).as( MyObject.clas );

JsonDiff diff1 = expected.diff( actual ); // same
```