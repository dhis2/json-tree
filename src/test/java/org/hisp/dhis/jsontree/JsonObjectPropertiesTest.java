package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonObject.Property;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedType;
import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Tests the extraction of properties provided by {@link JsonObject#properties(Class)}.
 * <p>
 * The coverage here is very shallow because the feature is used as part of the validation which has plenty of coverage
 * for different types, annotations and so on.
 *
 * @author Jan Bernitt
 */
class JsonObjectPropertiesTest {

    private static final ClassType STRING = new ClassType( String.class );

    public interface User extends JsonObject {

        default String username() {
            return getString( "username" ).string();
        }

        default String name() {
            return getString( "firstName" ).string() + " " + getString( "lastName" ).string();
        }

    }

    @Test
    void testString() {
        List<Property> properties = JsonObject.properties( User.class );
        Property expected = new Property( User.class, "username", JsonString.class, "username",
            STRING, null );
        assertPropertyExists( "username", expected, properties );
    }

    @Test
    void testString_Multiple() {
        List<Property> properties = JsonObject.properties( User.class );
        assertEquals( 3, properties.size() );
        assertEquals( Set.of( "username", "firstName", "lastName" ),
            properties.stream().map( Property::jsonName ).collect( toSet() ) );
        assertEquals( Set.of( "username", "name" ), properties.stream().map( Property::javaName ).collect( toSet() ) );

        assertPropertyExists( "firstName",
            new Property( User.class, "firstName", JsonString.class, "name", STRING, null ),
            properties );
        assertPropertyExists( "lastName",
            new Property( User.class, "lastName", JsonString.class, "name", STRING, null ),
            properties );
    }

    private void assertPropertyExists( String jsonName, Property expected, List<Property> actual ) {
        Property prop = actual.stream().filter( p -> p.jsonName().equals( jsonName ) ).findFirst()
            .orElse( null );
        assertNotNull( prop );
        assertSame( expected.in(), prop.in() );
        assertEquals( expected.jsonName(), prop.jsonName() );
        assertSame( expected.jsonType(), prop.jsonType() );
        assertEquals( expected.javaName(), prop.javaName() );
        assertSame( expected.javaType().getType(), prop.javaType().getType() );
    }

    record ClassType(Class<?> getType) implements AnnotatedType {

        @Override public <T extends Annotation> T getAnnotation( Class<T> aClass ) {
            return null;
        }

        @Override public Annotation[] getAnnotations() {
            return new Annotation[0];
        }

        @Override public Annotation[] getDeclaredAnnotations() {
            return new Annotation[0];
        }
    }
}
