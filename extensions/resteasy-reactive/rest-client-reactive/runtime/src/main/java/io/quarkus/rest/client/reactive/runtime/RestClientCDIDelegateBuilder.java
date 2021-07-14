package io.quarkus.rest.client.reactive.runtime;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import javax.net.ssl.HostnameVerifier;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.ext.QueryParamStyle;
import org.jboss.resteasy.reactive.client.api.QuarkusRestClientProperties;

import io.netty.handler.codec.http.multipart.HttpPostRequestEncoder;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import io.quarkus.restclient.config.RestClientConfig;
import io.quarkus.restclient.config.RestClientConfigRoot;
import io.quarkus.restclient.config.RestClientConfigRootProvider;

public class RestClientCDIDelegateBuilder<T> {

    private static final String MP_REST = "mp-rest";
    private static final String REST_FOLLOW_REDIRECTS = "%s/" + MP_REST + "/followRedirects";
    private static final String REST_HOSTNAME_VERIFIER = "%s/" + MP_REST + "/hostnameVerifier";
    private static final String REST_KEY_STORE = "%s/" + MP_REST + "/keyStore";
    private static final String REST_KEY_STORE_PASSWORD = "%s/" + MP_REST + "/keyStorePassword";
    private static final String REST_KEY_STORE_TYPE = "%s/" + MP_REST + "/keyStoreType";
    private static final String REST_PROVIDERS = "%s/" + MP_REST + "/providers";
    private static final String REST_PROXY_ADDRESS = "%s/" + MP_REST + "/proxyAddress";
    private static final String REST_QUERY_PARAM_STYLE = "%s/" + MP_REST + "/queryParamStyle";
    public static final String REST_SCOPE_FORMAT = "%s/" + MP_REST + "/scope";
    private static final String REST_TIMEOUT_CONNECT = "%s/" + MP_REST + "/connectTimeout";
    private static final String REST_TIMEOUT_READ = "%s/" + MP_REST + "/readTimeout";
    private static final String REST_TRUST_STORE = "%s/" + MP_REST + "/trustStore";
    private static final String REST_TRUST_STORE_PASSWORD = "%s/" + MP_REST + "/trustStorePassword";
    private static final String REST_TRUST_STORE_TYPE = "%s/" + MP_REST + "/trustStoreType";
    private static final String REST_URL_FORMAT = "%s/" + MP_REST + "/url";
    private static final String REST_URI_FORMAT = "%s/" + MP_REST + "/uri";

    private static final String MAX_REDIRECTS = "quarkus.rest.client.max-redirects";
    private static final String MULTIPART_POST_ENCODER_MODE = "quarkus.rest.client.multipart-post-encoder-mode";

    private final Class<T> jaxrsInterface;
    private final String baseUriFromAnnotation;
    private final String propertyPrefix;
    private final RestClientConfigRoot configRoot;
    private final RestClientConfig clientConfig;

    public static <T> T createDelegate(Class<T> jaxrsInterface, String baseUriFromAnnotation, String propertyPrefix) {
        return new RestClientCDIDelegateBuilder<T>(jaxrsInterface, baseUriFromAnnotation, propertyPrefix).build();
    }

    private RestClientCDIDelegateBuilder(Class<T> jaxrsInterface, String baseUriFromAnnotation, String propertyPrefix) {
        this(jaxrsInterface, baseUriFromAnnotation, propertyPrefix, getConfigRoot());
    }

    RestClientCDIDelegateBuilder(Class<T> jaxrsInterface, String baseUriFromAnnotation, String propertyPrefix,
            RestClientConfigRoot configRoot) {
        this.jaxrsInterface = jaxrsInterface;
        this.baseUriFromAnnotation = baseUriFromAnnotation;
        this.propertyPrefix = propertyPrefix;
        this.configRoot = configRoot;
        this.clientConfig = configRoot.configs.get(propertyPrefix);
    }

    private T build() {
        RestClientBuilder builder = RestClientBuilder.newBuilder();
        return build(builder);
    }

    T build(RestClientBuilder builder) {
        configureBaseUrl(builder);
        configureTimeouts(builder);
        configureProviders(builder);
        configureSsl(builder);
        configureRedirects(builder);
        configureQueryParamStyle(builder);
        configureProxy(builder);
        configureCustomProperties(builder);
        return builder.build(jaxrsInterface);
    }

    private void configureCustomProperties(RestClientBuilder builder) {
        Optional<String> encoder = getConfigPropertyWithFallback(RestClientConfigRoot::getMultipartPostEncoderMode,
                MULTIPART_POST_ENCODER_MODE, String.class);
        if (encoder.isPresent()) {
            HttpPostRequestEncoder.EncoderMode mode = HttpPostRequestEncoder.EncoderMode
                    .valueOf(encoder.get().toUpperCase(Locale.ROOT));
            builder.property(QuarkusRestClientProperties.MULTIPART_ENCODER_MODE, mode);
        }

        Optional<Integer> poolSize = getClientConfigProperty(RestClientConfig::getConnectionPoolSize);
        if (poolSize.isPresent()) {
            builder.property(QuarkusRestClientProperties.CONNECTION_POOL_SIZE, poolSize.get());
        }

        Optional<Integer> connectionTTL = getClientConfigProperty(RestClientConfig::getConnectionTTL);
        if (connectionTTL.isPresent()) {
            builder.property(QuarkusRestClientProperties.CONNECTION_TTL, connectionTTL.get());
        }
    }

    private void configureProxy(RestClientBuilder builder) {
        Optional<String> maybeProxy = getClientConfigPropertyWithFallback(RestClientConfig::getProxyAddress, REST_PROXY_ADDRESS,
                String.class);
        if (maybeProxy.isPresent()) {
            String proxyString = maybeProxy.get();

            int lastColonIndex = proxyString.lastIndexOf(':');

            if (lastColonIndex <= 0 || lastColonIndex == proxyString.length() - 1) {
                throw new RuntimeException("Invalid proxy string. Expected <hostname>:<port>, found '" + proxyString + "'");
            }

            String host = proxyString.substring(0, lastColonIndex);
            int port;
            try {
                port = Integer.parseInt(proxyString.substring(lastColonIndex + 1));
            } catch (NumberFormatException e) {
                throw new RuntimeException("Invalid proxy setting. The port is not a number in '" + proxyString + "'", e);
            }

            builder.proxyAddress(host, port);
        }
    }

    private void configureQueryParamStyle(RestClientBuilder builder) {
        Optional<QueryParamStyle> maybeQueryParamStyle = getClientConfigPropertyWithFallback(
                RestClientConfig::getQueryParamStyle, REST_QUERY_PARAM_STYLE, QueryParamStyle.class);
        if (maybeQueryParamStyle.isPresent()) {
            QueryParamStyle queryParamStyle = maybeQueryParamStyle.get();
            builder.queryParamStyle(queryParamStyle);
        }
    }

    private void configureRedirects(RestClientBuilder builder) {
        Optional<Integer> maxRedirects = getClientConfigProperty(RestClientConfig::getMaxRedirects);
        if (!maxRedirects.isPresent()) {
            maxRedirects = getOptionalProperty(MAX_REDIRECTS, Integer.class);
        }
        if (maxRedirects.isPresent()) {
            builder.property(QuarkusRestClientProperties.MAX_REDIRECTS, maxRedirects.get());
        }

        Optional<Boolean> maybeFollowRedirects = getClientConfigPropertyWithFallback(RestClientConfig::getFollowRedirects,
                REST_FOLLOW_REDIRECTS, Boolean.class);
        if (maybeFollowRedirects.isPresent()) {
            builder.followRedirects(maybeFollowRedirects.get());
        }
    }

    private void configureSsl(RestClientBuilder builder) {

        Optional<String> maybeTrustStore = getClientConfigPropertyWithFallback(RestClientConfig::getTrustStore,
                REST_TRUST_STORE,
                String.class);
        if (maybeTrustStore.isPresent()) {
            registerTrustStore(maybeTrustStore.get(), builder);
        }

        Optional<String> maybeKeyStore = getClientConfigPropertyWithFallback(RestClientConfig::getKeyStore, REST_KEY_STORE,
                String.class);
        if (maybeKeyStore.isPresent()) {
            registerKeyStore(maybeKeyStore.get(), builder);
        }

        Optional<String> maybeHostnameVerifier = getClientConfigPropertyWithFallback(RestClientConfig::getHostnameVerifier,
                REST_HOSTNAME_VERIFIER, String.class);
        if (maybeHostnameVerifier.isPresent()) {
            registerHostnameVerifier(maybeHostnameVerifier.get(), builder);
        }
    }

    private void registerHostnameVerifier(String verifier, RestClientBuilder builder) {
        try {
            Class<?> verifierClass = Thread.currentThread().getContextClassLoader().loadClass(verifier);
            builder.hostnameVerifier((HostnameVerifier) verifierClass.getDeclaredConstructor().newInstance());
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(
                    "Could not find a public, no-argument constructor for the hostname verifier class " + verifier, e);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find hostname verifier class " + verifier, e);
        } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(
                    "Failed to instantiate hostname verifier class " + verifier
                            + ". Make sure it has a public, no-argument constructor",
                    e);
        } catch (ClassCastException e) {
            throw new RuntimeException("The provided hostname verifier " + verifier + " is not an instance of HostnameVerifier",
                    e);
        }
    }

    private void registerKeyStore(String keyStorePath, RestClientBuilder builder) {
        Optional<String> keyStorePassword = getClientConfigPropertyWithFallback(RestClientConfig::getKeyStorePassword,
                REST_KEY_STORE_PASSWORD, String.class);
        Optional<String> keyStoreType = getClientConfigPropertyWithFallback(RestClientConfig::getKeyStoreType,
                REST_KEY_STORE_TYPE,
                String.class);

        try {
            KeyStore keyStore = KeyStore.getInstance(keyStoreType.orElse("JKS"));
            if (!keyStorePassword.isPresent()) {
                throw new IllegalArgumentException("No password provided for keystore");
            }
            String password = keyStorePassword.get();

            try (InputStream input = locateStream(keyStorePath)) {
                keyStore.load(input, password.toCharArray());
            } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Failed to initialize trust store from classpath resource " + keyStorePath,
                        e);
            }

            builder.keyStore(keyStore, password);
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + keyStorePath, e);
        }
    }

    private void registerTrustStore(String trustStorePath, RestClientBuilder builder) {
        Optional<String> maybeTrustStorePassword = getClientConfigPropertyWithFallback(RestClientConfig::getTrustStorePassword,
                REST_TRUST_STORE_PASSWORD, String.class);
        Optional<String> maybeTrustStoreType = getClientConfigPropertyWithFallback(RestClientConfig::getTrustStoreType,
                REST_TRUST_STORE_TYPE, String.class);

        try {
            KeyStore trustStore = KeyStore.getInstance(maybeTrustStoreType.orElse("JKS"));
            if (!maybeTrustStorePassword.isPresent()) {
                throw new IllegalArgumentException("No password provided for truststore");
            }
            String password = maybeTrustStorePassword.get();

            try (InputStream input = locateStream(trustStorePath)) {
                trustStore.load(input, password.toCharArray());
            } catch (IOException | CertificateException | NoSuchAlgorithmException e) {
                throw new IllegalArgumentException("Failed to initialize trust store from classpath resource " + trustStorePath,
                        e);
            }

            builder.trustStore(trustStore);
        } catch (KeyStoreException e) {
            throw new IllegalArgumentException("Failed to initialize trust store from " + trustStorePath, e);
        }
    }

    private InputStream locateStream(String path) throws FileNotFoundException {
        if (path.startsWith("classpath:")) {
            path = path.replaceFirst("classpath:", "");
            InputStream resultStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
            if (resultStream == null) {
                resultStream = getClass().getResourceAsStream(path);
            }
            if (resultStream == null) {
                throw new IllegalArgumentException(
                        "Classpath resource " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return resultStream;
        } else {
            if (path.startsWith("file:")) {
                path = path.replaceFirst("file:", "");
            }
            File certificateFile = new File(path);
            if (!certificateFile.isFile()) {
                throw new IllegalArgumentException(
                        "Certificate file: " + path + " not found for MicroProfile Rest Client SSL configuration");
            }
            return new FileInputStream(certificateFile);
        }
    }

    private void configureProviders(RestClientBuilder builder) {
        Optional<String> maybeProviders = getClientConfigPropertyWithFallback(RestClientConfig::getProviders, REST_PROVIDERS,
                String.class);
        if (maybeProviders.isPresent()) {
            registerProviders(builder, maybeProviders.get());
        }
    }

    private void registerProviders(RestClientBuilder builder, String providersAsString) {
        for (String s : providersAsString.split(",")) {
            builder.register(providerClassForName(s.trim()));
        }
    }

    private Class<?> providerClassForName(String name) {
        try {
            return Class.forName(name, true, Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not find provider class: " + name);
        }
    }

    private void configureTimeouts(RestClientBuilder builder) {
        Optional<Long> connectTimeout = getClientConfigPropertyWithFallback(RestClientConfig::getConnectTimeout,
                REST_TIMEOUT_CONNECT,
                Long.class);
        if (connectTimeout.isPresent()) {
            builder.connectTimeout(connectTimeout.get(), TimeUnit.MILLISECONDS);
        }

        Optional<Long> readTimeout = getClientConfigPropertyWithFallback(RestClientConfig::getReadTimeout, REST_TIMEOUT_READ,
                Long.class);
        if (readTimeout.isPresent()) {
            builder.readTimeout(readTimeout.get(), TimeUnit.MILLISECONDS);
        }
    }

    private void configureBaseUrl(RestClientBuilder builder) {
        Optional<String> propertyOptional = getClientConfigPropertyWithFallback(RestClientConfig::getUri, REST_URI_FORMAT,
                String.class);
        if (!propertyOptional.isPresent()) {
            propertyOptional = getClientConfigPropertyWithFallback(RestClientConfig::getUrl, REST_URL_FORMAT, String.class);
        }
        if (((baseUriFromAnnotation == null) || baseUriFromAnnotation.isEmpty())
                && !propertyOptional.isPresent()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Unable to determine the proper baseUrl/baseUri. " +
                                    "Consider registering using @RegisterRestClient(baseUri=\"someuri\"), @RegisterRestClient(configKey=\"orkey\"), "
                                    +
                                    "or by adding '%s' or '%s' to your Quarkus configuration",
                            String.format(REST_URL_FORMAT, propertyPrefix), String.format(REST_URI_FORMAT, propertyPrefix)));
        }
        String baseUrl = propertyOptional.orElse(baseUriFromAnnotation);

        try {
            builder.baseUrl(new URL(baseUrl));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The value of URL was invalid " + baseUrl, e);
        } catch (Exception e) {
            if ("com.oracle.svm.core.jdk.UnsupportedFeatureError".equals(e.getClass().getCanonicalName())) {
                throw new IllegalArgumentException(baseUrl
                        + " requires SSL support but it is disabled. You probably have set quarkus.ssl.native to false.");
            }
            throw e;
        }
    }

    private <PropertyType> Optional<PropertyType> getOptionalDynamicProperty(String propertyFormat, Class<PropertyType> type) {
        final Config config = ConfigProvider.getConfig();
        Optional<PropertyType> interfaceNameValue = config
                .getOptionalValue(String.format(propertyFormat, jaxrsInterface.getName()), type);
        return interfaceNameValue.isPresent() ? interfaceNameValue
                : config.getOptionalValue(String.format(propertyFormat, propertyPrefix), type);
    }

    private <PropertyType> Optional<PropertyType> getOptionalProperty(String propertyName, Class<PropertyType> type) {
        final Config config = ConfigProvider.getConfig();
        return config.getOptionalValue(propertyName, type);
    }

    private <PropertyType> Optional<PropertyType> getClientConfigPropertyWithFallback(
            Function<RestClientConfig, Optional<PropertyType>> method, String propertyFormat, Class<PropertyType> type) {
        if (clientConfig != null) {
            Optional<PropertyType> configOptional = method.apply(clientConfig);
            if (configOptional.isPresent()) {
                return configOptional;
            }
        }
        return getOptionalDynamicProperty(propertyFormat, type);
    }

    private <PropertyType> Optional<PropertyType> getClientConfigProperty(
            Function<RestClientConfig, Optional<PropertyType>> method) {
        if (clientConfig != null) {
            Optional<PropertyType> configOptional = method.apply(clientConfig);
            if (configOptional.isPresent()) {
                return configOptional;
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("SameParameterValue")
    private <PropertyType> Optional<PropertyType> getConfigPropertyWithFallback(
            Function<RestClientConfigRoot, Optional<PropertyType>> method, String propertyFormat, Class<PropertyType> type) {
        if (configRoot != null) {
            Optional<PropertyType> configOptional = method.apply(configRoot);
            if (configOptional.isPresent()) {
                return configOptional;
            }
        }
        return getOptionalProperty(propertyFormat, type);
    }

    private static RestClientConfigRoot getConfigRoot() {
        InstanceHandle<RestClientConfigRootProvider> configHandle = Arc.container()
                .instance(RestClientConfigRootProvider.class);
        if (!configHandle.isAvailable()) {
            throw new IllegalStateException("Unable to find the RestClientConfigRootProvider");
        }
        return configHandle.get().getConfigRoot();
    }

}
