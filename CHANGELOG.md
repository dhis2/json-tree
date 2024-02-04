# ChangeLog

## [Unreleased] v1.1

**Features** 
* Added: _json-patch_ support, see `JsonPatch`, `JsonPointer`
* Added: node bulk modification API: `JsonNode#patch` + `JsonNode.Insert`, `JsonNode.Remove`
* Added: test for same information `JsonValue#equivalentTo`
* Added: test for same definition (ignoring formatting) `JsonValue#identicalTo`
* Added: `@Validation#acceptNull` to declare that `null` value satisfies required

**Breaking Changes**

**Bugfixes**