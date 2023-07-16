package cowj;

import org.junit.*;
import spark.Filter;
import spark.Request;
import spark.Response;
import zoomba.lang.core.types.ZTypes;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SchemaTest {

    @BeforeClass
    public static void before(){
        mr = ModelRunnerTest.runModel("samples/prod/prod.yaml" );
    }

    @AfterClass
    public static void after(){
        if ( mr == null ) return;
        mr.stop();
        mr = null;
    }

    @Test
    public void loadSchemaTest(){
        TypeSystem typeSystem = TypeSystem.fromFile( "samples/prod/static/types/schema.yaml");
        Assert.assertFalse( typeSystem.routes().isEmpty() );
        Assert.assertEquals( 2, typeSystem.routes().size());
    }

    @Test
    public void loadWrongFile(){
        TypeSystem ts = TypeSystem.fromFile( "samples/prod/static/types/schema111.yaml");
        Assert.assertEquals( TypeSystem.NULL, ts);
    }
    static ModelRunner mr;

    @Test
    public void validSchemaTest(){
        String r = ModelRunnerTest.get( "http://localhost:5042", "/person/foobar");
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("foobar") );
        Assert.assertTrue( r.contains("not found") );
        String body = ZTypes.jsonString(Map.of( "firstName", "foo", "lastName", "bar"));
        r = ModelRunnerTest.post( "http://localhost:5042", "/person",  body);
        Assert.assertNotNull(r);
        Map<String,String> m = (Map)ZTypes.json(r);
        String id = m.get("personId");
        Assert.assertNotNull(id);
        r = ModelRunnerTest.get( "http://localhost:5042", "/person/" + id );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains(id) );
    }

    @Test
    public void invalidJSONSchemaTest(){
        // NOT EVEN JSON test
        String r = ModelRunnerTest.post( "http://localhost:5042", "/person",  "foo bar!" );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("Validation") );
        // Missing Field Test
        String body = ZTypes.jsonString(Map.of( "firstName", "foo" ));
        r = ModelRunnerTest.post( "http://localhost:5042", "/person",  "foo bar!" );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("Validation") );
        // Higher Age than 150 ...
        body = ZTypes.jsonString(Map.of( "firstName", "foo", "lastName", "bar", "age" , 250 ));
        r = ModelRunnerTest.post( "http://localhost:5042", "/person",  "foo bar!" );
        Assert.assertNotNull(r);
        Assert.assertTrue( r.contains("Validation") );
    }

    @Test
    public void invalidExpressionTest(){
        Assert.assertFalse(TypeSystem.testExpression(null, null, "foo"));
    }

    @Test
    public void inVerificationBranchesTest() throws Exception {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        Map<String,Object> nullSig = new HashMap<>();
        nullSig.put("put", Collections.emptyMap());
        when(request.requestMethod()).thenReturn("post");
        TypeSystem ts = TypeSystem.fromConfig( Map.of( TypeSystem.ROUTES,
                Map.of("/foo", nullSig)) , "");
        Filter f = ts.inputSchemaVerificationFilter( "/foo");
        f.handle( request, response); // should be no error here
        Assert.assertTrue(true);

        ts = TypeSystem.fromConfig( Map.of( TypeSystem.ROUTES,
                Map.of("/foo", Map.of("post", Map.of( "in", "" )))) , "");
        f = ts.inputSchemaVerificationFilter( "/foo");
        f.handle( request, response); // should be no error here
        Assert.assertTrue(true);
    }

    @Test
    public void outVerificationBranchesTest() throws Exception {
        Request request = mock(Request.class);
        Response response = mock(Response.class);
        Map<String,Object> nullSig = new HashMap<>();
        nullSig.put("put", Collections.emptyMap());
        when(request.requestMethod()).thenReturn("post");
        TypeSystem ts = TypeSystem.fromConfig( Map.of( TypeSystem.ROUTES,
                Map.of("/foo", nullSig)) , "");
        Filter f = ts.outputSchemaVerificationFilter( "/foo");
        f.handle( request, response); // should be no error here
        Assert.assertTrue(true);

        ts = TypeSystem.fromConfig( Map.of(
                TypeSystem.LABELS, Map.of( "xx", "" ),
                TypeSystem.ROUTES,
                Map.of("/foo", Map.of("post", Map.of(
                        "in", "foo.json",
                        "zz", ""
                )))) , "");
        f = ts.outputSchemaVerificationFilter( "/foo");
        f.handle( request, response); // should be no error here
        Assert.assertTrue(true);

        ts = TypeSystem.fromConfig( Map.of(
                TypeSystem.LABELS, Map.of( "xx", "true" ),
                TypeSystem.ROUTES,
                Map.of("/foo", Map.of("post", Map.of(
                        "in", "foo.json",
                        "xx", ""
                )))) , "");
        f = ts.outputSchemaVerificationFilter( "/foo");
        f.handle( request, response); // should be no error here
        Assert.assertTrue(true);

    }
}
