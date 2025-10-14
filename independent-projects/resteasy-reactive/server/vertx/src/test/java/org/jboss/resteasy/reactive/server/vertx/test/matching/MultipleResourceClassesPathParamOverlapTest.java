package org.jboss.resteasy.reactive.server.vertx.test.matching;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.jboss.resteasy.reactive.RestPath;
import org.jboss.resteasy.reactive.server.vertx.test.framework.ResteasyReactiveUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.function.Supplier;

import static io.restassured.RestAssured.get;
import static org.hamcrest.Matchers.equalTo;

public class MultipleResourceClassesPathParamOverlapTest {

    @RegisterExtension
    static ResteasyReactiveUnitTest test = new ResteasyReactiveUnitTest()
            .setArchiveProducer(new Supplier<>() {
                @Override
                public JavaArchive get() {
                    return ShrinkWrap.create(JavaArchive.class)
                            .addClasses(TestResource.class, AnotherResource.class);
                }
            });

    @Test
    public void test() {
        get("/hello/foo")
                .then()
                .statusCode(404);

        get("/hello/foo/value")
                .then()
                .statusCode(200)
                .body(equalTo("Foo value"));

        get("/hello/foo/bar")
                .then()
                .statusCode(200)
                .body(equalTo("Foo bar"));

        get("/hello/foo/bar/value")
                .then()
                .statusCode(200)
                .body(equalTo("FooBar value"));

        get("/hello/foo/bah_value")
                .then()
                .statusCode(200)
                .body(equalTo("Foo bah_value"));

        get("/hello/foo/bar_value")
                .then()
                .statusCode(200)
                .body(equalTo("Foo bar_value"));

        get("/hello/foo/foo/bar")
                .then()
                .statusCode(200)
                .body(equalTo("FooBarFooBar"));

    }

    @Path("/hello")
    public static class TestResource {

        @GET
        @Path("/foo/{param}")
        public String foo(@RestPath String param) {
            return "Foo " + param;
        }

        @GET
        @Path("/foo/bar/{param}")
        public String fooBar(@RestPath String param) {
            return "FooBar " + param;
        }

    }


    @Path("/hello/foo/bar")
    public static class AnotherResource {

        @GET
        @Path("/foo/bar")
        public String fooBar() {
            return "FooBarFooBar";
        }

    }

}
