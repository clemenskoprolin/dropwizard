package benchmarks.edgecases;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Edge case: fluent builder with return-this chaining and validation.
 *
 * Hypothesis: J2K converts fluent setters (returning `this`) to regular
 * Kotlin functions returning `Unit` or `this@Builder`, losing the fluent
 * chain unless the converter recognises the `return this` pattern. If it does
 * recognise it, it may produce an `also { }` chain or keep explicit returns.
 * Builder classes with covariant return types in subclasses are particularly
 * at risk of being broken by the conversion.
 */
public class BuilderFluent {

    private final String host;
    private final int port;
    private final Duration timeout;
    private final boolean tls;
    private final List<String> headers;

    private BuilderFluent(Builder builder) {
        this.host    = Objects.requireNonNull(builder.host, "host");
        this.port    = builder.port;
        this.timeout = builder.timeout;
        this.tls     = builder.tls;
        this.headers = Collections.unmodifiableList(new ArrayList<>(builder.headers));
    }

    public String getHost()          { return host; }
    public int getPort()             { return port; }
    public Duration getTimeout()     { return timeout; }
    public boolean isTls()           { return tls; }
    public List<String> getHeaders() { return headers; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String host;
        private int port = 8080;
        private Duration timeout = Duration.ofSeconds(30);
        private boolean tls = false;
        private final List<String> headers = new ArrayList<>();

        public Builder host(String host) {
            this.host = Objects.requireNonNull(host);
            return this;
        }

        public Builder port(int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("invalid port: " + port);
            this.port = port;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        public Builder tls(boolean tls) {
            this.tls = tls;
            return this;
        }

        public Builder header(String name, String value) {
            this.headers.add(name + ": " + value);
            return this;
        }

        public BuilderFluent build() {
            return new BuilderFluent(this);
        }
    }

    // Subclass builder demonstrating covariant return
    public static class SecureBuilder extends Builder {
        private String certificatePath;

        public SecureBuilder certificate(String path) {
            this.certificatePath = path;
            return this;   // returns SecureBuilder, not Builder
        }

        @Override
        public SecureBuilder tls(boolean tls) {
            super.tls(tls);
            return this;
        }

        @Override
        public BuilderFluent build() {
            tls(true);
            return super.build();
        }
    }
}
