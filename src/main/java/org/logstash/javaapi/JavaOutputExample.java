package org.logstash.javaapi;

import co.elastic.logstash.api.Configuration;
import co.elastic.logstash.api.Context;
import co.elastic.logstash.api.LogstashPlugin;
import co.elastic.logstash.api.PluginConfigSpec;
import co.elastic.logstash.api.v0.Output;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.logstash.Event;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;

// class name must match plugin name
@LogstashPlugin(name="java_output_example")
public class JavaOutputExample implements Output {

    public static final PluginConfigSpec<String> PREFIX_CONFIG =
            Configuration.stringSetting("prefix", "");

    private String prefix;
    private PrintStream printer;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped = false;

    // all plugins must provide a constructor that accepts Configuration and Context
    public JavaOutputExample(final Configuration configuration, final Context context) {
        this(configuration, context, System.out);
    }

    JavaOutputExample(final Configuration config, final Context context, OutputStream targetStream) {
        // constructors should validate configuration options
        prefix = config.get(PREFIX_CONFIG);
        printer = new PrintStream(targetStream);
    }

    @Override
    public void output(final Collection<Event> events) {
        try {
            Iterator<Event> z = events.iterator();
            while (z.hasNext() && !stopped) {
                String s = prefix + z.next().toJson();
                printer.println(s);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void stop() {
        stopped = true;
        done.countDown();
    }

    @Override
    public void awaitStop() throws InterruptedException {
        done.await();
    }

    @Override
    public Collection<PluginConfigSpec<?>> configSchema() {
        // should return a list of all configuration options for this plugin
        return Collections.singletonList(PREFIX_CONFIG);
    }
}
