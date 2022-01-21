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
```java
JsonString str = root.getObject("a").getArray("b").getString(3)
// or
JsonString str = root.getString("a.b[3]")
```

Assertions using standard JUnit asserts
```java
JsonObject settings = GET( "/me/settings" ).content( HttpStatus.OK );

assertTrue( settings.isObject() );
assertFalse( settings.isEmpty() );
assertTrue( settings.get( "keyMessageSmsNotification" ).exists() );
assertEquals( "en", settings.getString( "keyUiLocale" ).string() );
```
Virtual tree: no exceptions before assert 🤩 

Asserting a Schema or Shape
```java
assertTrue ( settings.isA( JsonSettings.class) );
```
Recursivly checks that all `@Expected` members of `JsonSettings` interface are present.

## Advanced Features

**Custom node types**
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
```java
JsonPasswordValidation result = POST( "/me/validatePassword", "text/plain:$ecrEt42" )
    .content().as( JsonPasswordValidation.class );
assertTrue( result.isValidPassword() );
assertNull( result.getErrorMessage() );
```

**Asserting Structure**

Annotate getters with `@Expected`
```java
JsonObject result = POST( "/me/validatePassword", "text/plain:$ecrEt42" ).content()
assertTrue(result.isA(JsonPasswordValidation.class));
// or
JsonPasswordValidation result = POST( "/me/validatePassword", "text/plain:$ecrEt42" )
    .content().asObject( JsonPasswordValidation.class );
```

Traversing actual tree to find nodes or manipulate it
* `JsonValue#node()`: from virtual to actual JSON tree 🗱
* `JsonObect#find`: return first matching `JsonValue`
* `JsonNode#visit`: visit matching nodes
* `JsonNode#find`: return first matching `JsonNode`
* `JsonNode#count`: count matching nodes

```java
JsonWebMessage message = assertWebMessage(...);
JsonErrorReport error = message.find( JsonErrorReport.class,
            report -> report.getErrorCode() == ErrorCode.E4031 );
assertEquals("expected message", error.getMessage() );
```
