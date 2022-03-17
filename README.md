# json-tree

[![main](https://github.com/dhis2/json-tree/actions/workflows/main.yml/badge.svg?branch=main)](https://github.com/dhis2/json-tree/actions/workflows/main.yml)

A lazily parsed JSON object tree library

## Virtual vs Actual Tree

A `JsonResponse` is a **virtual** tree of `JsonValue` nodes:

* `JsonObject`
* `JsonArray`
* `JsonNumber`
* `JsonString`
* `JsonBoolean`
* `JsonNull`

Underlying implementation of the actual JSON document is the `JsonDocument`
which exposes all node types as `JsonNode`.

| `JsonValue` API | `JsonNode` API |
| --------------- | -------------- |
| **virtual** 👻  | **actual** ☠️   |
| high level abstraction | low level abstraction |
| what we expect we got back | what we actually got back |
| navigation without requiring existence (no exceptions) | navigation demands existence (otherwise throws exceptions) |
| extendable tree API | non-extendable tree API |

"Casting" to Node Types

* `JsonValue   as(Class<T extends JsonValue> type)`
* `JsonList<T> asList(Class<T extends JsonValue> type)`
* `JsonMap<V>  asMap(Class<V extends JsonValue> type)`
* and more of similar kind...

Tree Navigation

* `JsonValue  get(String path)`
* `JsonObject getObject(String path)`
* `JsonArray  getArray(String path)`
* `JsonNumber getNumber(String path)`
* `JsonBoolean getBoolean(String path)`
* `JsonValue  get(int index)`
* and more of similar kind...

Drilling down...

    JsonString str=root.getObject("a").getArray("b").getString(3)
    // or
    JsonString str=root.getString("a.b[3]")

Traversing actual tree to find nodes or manipulate it

* `JsonValue#node()`: from virtual to actual JSON tree 🗱
* `JsonObect#find`: return first matching `JsonValue`
* `JsonNode#visit`: visit matching nodes
* `JsonNode#find`: return first matching `JsonNode`
* `JsonNode#count`: count matching nodes

Assertions using standard JUnit asserts

    JsonObject settings = GET("/me/settings").content(HttpStatus.OK);
    
    assertTrue(settings.isObject());
    assertFalse(settings.isEmpty());
    assertTrue(settings.get("keyMessageSmsNotification").exists());
    assertEquals("en",settings.getString("keyUiLocale").string());

Virtual tree: no exceptions before assert 🤩

## Custom Node Types

The `JsonValue` API can easily be extended with user defined types by simply declaring an interface with `default`
methods.

```java
public interface JsonPasswordValidation extends JsonObject
{
    default boolean isValidPassword()
    {
        return getBoolean( "isValidPassword" ).booleanValue();
    }

    default String getErrorMessage()
    {
        return getString( "errorMessage" ).string();
    }
}
```

Use `as` or `asObject` to "cast" a `JsonValue` to a custom type:

    JsonValue jsonValue = fetchPasswordValidation();
    JsonPasswordValidation result = jsonValue.as(JsonPasswordValidation.class );
    assertTrue(result.isValidPassword());
    assertNull(result.getErrorMessage());

Asserting a Schema or Structure

    assertTrue(settings.isA(JsonPasswordValidation.class));

Recursively checks that all primitive members or members annotated with `@Expected` as defined by the `JsonSettings`
interface are present.

When `asObject` is used instead of `as` the structural check is done implicitly and fails with an exception should the
actual JSON not match the expectations defined by the provided user defined interface.

## Building or Streaming JSON

The `JsonBuilder` API is used to:

* create a `JsonNode` document (and from there a JSON `String`)
* directly stream JSON to an output stream while it is built

While it is an "append-only" API to support true streaming its use of lambda function arguments allows to compose the
output in a convenient and flexible way.

Ad-hoc creation of `JsonNode`:

    JsonNode obj = JsonBuilder.createObject(
        obj -> obj.addString( "name","value" ));
    
    String json = obj.getDeclaration();

Directly writing JSON to an output stream:

    JsonBuilder.streamObject( out, 
        obj -> obj.addString( "name", "value" ) );

## Deploy to Maven Repository

This library can be deployed as an artefact to the Sonatype OSS Maven repository. The deployment plugins are defined in
a separate profile to avoid performing GPG key signing during regular build processes. To deploy invoke:

	mvn clean deploy -P deploy

