package io.quarkus.restclient.runtime;

import io.quarkus.arc.Arc;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RestClientBaseTest {

    @Test
    public void test() {
        Config config = ConfigProvider.getConfig();

        Arc.container().beanManager().createBean()

        RestClientBase restClientBase = new RestClientBase(AType.class, "http://host/path", "prefix", new Class[]{});
        Object client = restClientBase.create();
        Assertions.assertNotNull(client);
    }
    
    private static class AType {}
}
