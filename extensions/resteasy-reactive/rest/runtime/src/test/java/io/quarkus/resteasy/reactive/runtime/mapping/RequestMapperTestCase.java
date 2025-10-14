package io.quarkus.resteasy.reactive.runtime.mapping;

import java.util.ArrayList;

import org.jboss.resteasy.reactive.server.mapping.RequestMapper;
import org.jboss.resteasy.reactive.server.mapping.URITemplate;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RequestMapperTestCase {

    @Test
    void testMap() {

        RequestMapper<String> mapper = mapper(false, "/id", "/id/{param}", "/bar/{p1}/{p2}", "/bar/{p1}");
        mapper.dump();

        RequestMapper.RequestMatch<String> result = mapper.map("/bar/34/44");
        Assertions.assertEquals("/bar/{p1}/{p2}", result.value);
        Assertions.assertEquals("34", result.pathParamValues[0]);
        Assertions.assertEquals("44", result.pathParamValues[1]);
        Assertions.assertNull(mapper.map("/foo"));
        Assertions.assertEquals("/id", mapper.map("/id").value);
        result = mapper.map("/id/34");
        Assertions.assertEquals("/id/{param}", result.value);
        Assertions.assertEquals("34", result.pathParamValues[0]);
        result = mapper.map("/id/34/");
        Assertions.assertNotNull(result);
        Assertions.assertEquals("/id/{param}", result.value);
        Assertions.assertEquals("34", result.pathParamValues[0]);
        result = mapper.map("/bar/34");
        Assertions.assertEquals("/bar/{p1}", result.value);
        Assertions.assertEquals("34", result.pathParamValues[0]);
    }

    @Test
    public void testMapAll() {
        RequestMapper<String> mapper = mapper(true, "/greetings", "/greetings/{id}", "/greetings/unrelated");
        mapper.dump();

        var results = mapper.mapAll("/not-existing");
        Assertions.assertTrue(results.isEmpty());

        results = mapper.mapAll("/greetings/greeting-id");
        Assertions.assertFalse(results.isEmpty());
        // Should have two matches: /greetings and /greetings/{id}
        Assertions.assertEquals(2, results.size());
        // First match should be /greetings with remaining="/greeting-id"
        Assertions.assertEquals("/greetings", results.get(0).value);
        Assertions.assertEquals("/greeting-id", results.get(0).remaining);
        // Second match should be /greetings/{id} with remaining=""
        Assertions.assertEquals("/greetings/{id}", results.get(1).value);
        Assertions.assertEquals("", results.get(1).remaining);
        Assertions.assertEquals("greeting-id", results.get(1).pathParamValues[0]);
    }

    RequestMapper<String> mapper(boolean prefixTemplates, String... vals) {
        ArrayList<RequestMapper.RequestPath<String>> list = new ArrayList<>();
        for (String i : vals) {
            list.add(new RequestMapper.RequestPath<>(prefixTemplates, new URITemplate(i, false), i));
        }
        return new RequestMapper<>(list);
    }

}
