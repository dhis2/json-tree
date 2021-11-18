# json-tree
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

`JsonValue`s are **virtual** 👻:
* high level abstraction
* what we believe we got back
* navigation without requiring existence
* extendable tree

`JsonNode`s are **actual** ☠️:
* low level abstraction
* what we actually got back
* navigation demands existence
* not extendable tree

Tree Navigation

* `JsonValue  get(String path)`
* `JsonObject getObject(String path)`
* `JsonArray  getArray(String path)`
* `JsonNumber getNumber(String path)`
* `JsonBoolean getBoolean(String path)`
* `JsonValue  get(int index)`
* and some more...

Drilling down...
```java
JsonString str = root.getObject("a").getArray("b").getString(3)
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

Special asserts:
```java
String userId = assertStatus(HttpStatus.CREATED, POST("/users/", userJson));
JsonWebMessage message = assertWebMessage(....);
```

## Advanced Features
Custom node types:
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

Asserting Structure
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
