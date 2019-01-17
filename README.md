# Logstash Java Plugin

[![Travis Build Status](https://travis-ci.org/logstash-plugins/logstash-output-java_output_example.svg)](https://travis-ci.org/logstash-plugins/logstash-output-java_output_example)

This is a Java plugin for [Logstash](https://github.com/elastic/logstash).

It is fully free and fully open source. The license is Apache 2.0, meaning you are free to use it however you want.

## How to write a Java output

> <b>IMPORTANT NOTE:</b> Native support for Java plugins in Logstash is in the experimental phase. While unnecessary
changes will be avoided, anything may change in future phases. See the ongoing work on the 
[beta phase](https://github.com/elastic/logstash/pull/10232) of Java plugin support for the most up-to-date status.

### Overview 

Native support for Java plugins in Logstash consists of several components including:
* Extensions to the Java execution engine to support running Java plugins in Logstash pipelines
* APIs for developing Java plugins. The APIs are in the `co.elastic.logstash.api` package. If a Java plugin 
references any classes or specific concrete implementations of API interfaces outside that package, breakage may 
occur because the implementation of classes outside of the API package may change at any time.
* Tooling to automate the packaging and deployment of Java plugins in Logstash [not complete as of the experimental phase]

To develop a new Java output for Logstash, you write a new Java class that conforms to the Logstash Java Output
API, package it, and install it with the `logstash-plugin` utility. We'll go through each of those steps in this guide.

### Coding the plugin

It is recommended that you start by copying the 
[example output plugin](https://github.com/logstash-plugins/logstash-output-java_output_example). The example output
plugin prints events in JSON format to the console. Let's look at the main class in that example output:
 
```java
@LogstashPlugin(name="java_output_example")
public class JavaOutputExample implements Output {

    public static final PluginConfigSpec<String> PREFIX_CONFIG =
            Configuration.stringSetting("prefix", "");

    private String prefix;
    private PrintStream printer;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile boolean stopped = false;

    public JavaOutputExample(final Configuration configuration, final Context context) {
        this(configuration, context, System.out);
    }

    JavaOutputExample(final Configuration config, final Context context, OutputStream targetStream) {
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
        return Collections.singletonList(PREFIX_CONFIG);
    }
}
```

Let's step through and examine each part of that class.

#### Class declaration
```java
@LogstashPlugin(name="java_output_example")
public class JavaOutputExample implements Output {
```
There are two things to note about the class declaration:
* All Java plugins must be annotated with the `@LogstashPlugin` annotation. Additionally:
  * The `name` property of the annotation must be supplied and defines the name of the plugin as it will be used
   in the Logstash pipeline definition. For example, this output would be referenced in the output section of the
   Logstash pipeline definition as `output { java_output_example => { .... } }`
  * The value of the `name` property must match the name of the class excluding casing and underscores.
* The class must implement the `co.elastic.logstash.api.v0.Output` interface.

#### Plugin settings

The snippet below contains both the setting definition and the method referencing it:
```java
public static final PluginConfigSpec<String> PREFIX_CONFIG =
        Configuration.stringSetting("prefix", "");

@Override
public Collection<PluginConfigSpec<?>> configSchema() {
    return Collections.singletonList(PREFIX_CONFIG);
}
```
The `PluginConfigSpec` class allows developers to specify the settings that a plugin supports complete with setting 
name, data type, deprecation status, required status, and default value. In this example, the `prefix` setting defines
an optional prefix to include in the output of the event. The setting is not required and if it is not explicitly set,
it defaults to the empty string.

The `configSchema` method must return a list of all settings that the plugin supports. In a future phase of the
Java plugin project, the Logstash execution engine will validate that all required settings are present and that
no unsupported settings are present.

#### Constructor and initialization
```java
private String prefix;
private PrintStream printer;

public JavaOutputExample(final Configuration configuration, final Context context) {
    this(configuration, context, System.out);
}

JavaOutputExample(final Configuration config, final Context context, OutputStream targetStream) {
    prefix = config.get(PREFIX_CONFIG);
    printer = new PrintStream(targetStream);
}
```
All Java output plugins must have a constructor taking both a `Configuration` and `Context` argument. This is the
constructor that will be used to instantiate them at runtime. The retrieval and validation of all plugin settings
should occur in this constructor. In this example, the values of the `prefix` setting is retrieved and stored in
a local variable for later use in the `output` method. In this example, a second, pacakge private constructor is
defined that is useful for unit testing with a `Stream` other than `System.out`.

Any additional initialization may occur in the constructor as well. If there are any unrecoverable errors encountered
in the configuration or initialization of the output plugin, a descriptive exception should be thrown. The exception
will be logged and will prevent Logstash from starting.

#### Output method
```java
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
```
Outputs may send events to local sinks such as the console or a file or to remote systems such as Elasticsearch
or other external systems. In this example, the events are printed to the local console.

#### Stop and awaitStop methods

```java
private final CountDownLatch done = new CountDownLatch(1);
private volatile boolean stopped;

@Override
public void stop() {
    stopped = true;
    done.countDown();
}

@Override
public void awaitStop() throws InterruptedException {
    done.await(); 
}
```
The `stop` method notifies the output to stop sending events. The stop mechanism may be implemented in any way
that honors the API contract though a `volatile boolean` flag works well for many use cases. Because this output
example is so simple, its `output` method does not check for the stop flag.

Outputs stop both asynchronously and cooperatively. Use the `awaitStop` method to block until the output has 
completed the stop process. Note that this method should **not** signal the output to stop as the `stop` method 
does. The awaitStop mechanism may be implemented in any way that honors the API contract though a `CountDownLatch`
works well for many use cases.

#### Unit tests
Lastly, but certainly not least importantly, unit tests are strongly encouraged. The example output plugin includes
an [example unit test](https://github.com/logstash-plugins/logstash-output-java_output_example/blob/master/src/test/java/org/logstash/javaapi/JavaOutputExampleTest.java)
that you can use as a template for your own.

### Packaging and deployment

For the purposes of dependency management and interoperability with Ruby plugins, Java plugins will be packaged
as Ruby gems. One of the goals for Java plugin support is to eliminate the need for any knowledge of Ruby or its
toolchain for Java plugin development. Future phases of the Java plugin project will automate the packaging of
Java plugins as Ruby gems so no direct knowledge of or interaction with Ruby will be required. In the experimental
phase, Java plugins must still be manually packaged as Ruby gems and installed with the `logstash-plugin` utility.

#### Compile to JAR file

The Java plugin should be compiled and assembled into a fat jar with the `vendor` task in the Gradle build file. This
will package all Java dependencies into a single jar and write it to the correct folder for later packaging into
a Ruby gem.

#### Manual packaging as Ruby gem 

Several Ruby source files are required to correctly package the jar file as a Ruby gem. These Ruby files are used
only at Logstash startup time to identify the Java plugin and are not used during runtime event processing. In a 
future phase of the Java plugin support project, these Ruby source files will be automatically generated. 

`logstash-output-<output-name>.gemspec`
```
Gem::Specification.new do |s|
  s.name            = 'logstash-output-java_output_example'
  s.version         = '0.0.1'
  s.licenses        = ['Apache-2.0']
  s.summary         = "Example output using Java plugin API"
  s.description     = ""
  s.authors         = ['Elasticsearch']
  s.email           = 'info@elastic.co'
  s.homepage        = "http://www.elastic.co/guide/en/logstash/current/index.html"
  s.require_paths = ['lib', 'vendor/jar-dependencies']

  # Files
  s.files = Dir["lib/**/*","spec/**/*","*.gemspec","*.md","CONTRIBUTORS","Gemfile","LICENSE","NOTICE.TXT", "vendor/jar-dependencies/**/*.jar", "vendor/jar-dependencies/**/*.rb", "VERSION", "docs/**/*"]

  # Special flag to let us know this is actually a logstash plugin
  s.metadata = { 'logstash_plugin' => 'true', 'logstash_group' => 'output'}

  # Gem dependencies
  s.add_runtime_dependency "logstash-core-plugin-api", ">= 1.60", "<= 2.99"
  s.add_runtime_dependency 'jar-dependencies'

  s.add_development_dependency 'logstash-devutils'
end
```
The above file can be used unmodified except that `s.name` must follow the `logstash-output-<output-name>` pattern
and `s.version` must match the `project.version` specified in the `build.gradle` file.

`lib/logstash/outputs/<output-name>.rb`
```
# encoding: utf-8
require "logstash/outputs/base"
require "logstash/namespace"
require "logstash-output-java_output_example_jars"
require "java"

class LogStash::Outputs::JavaOutputExample < LogStash::Outputs::Base
  config_name "java_output_example"

  def self.javaClass() org.logstash.javaapi.JavaOutputExample.java_class; end
end
```
The following items should be modified in the file above:
1. It should be named to correspond with the output name.
1. `require "logstash-output-java_output_example_jars"` should be changed to reference the appropriate "jars" file
as described below.
1. `class LogStash::Outputs::JavaOutputExample < LogStash::Outputs::Base` should be changed to provide a unique and
descriptive Ruby class name.
1. `config_name "java_output_example"` must match the name of the plugin as specified in the `name` property of
the `@LogstashPlugin` annotation.
1. `def self.javaClass() org.logstash.javaapi.JavaOutputExample.java_class; end` must be modified to return the
class of the Java output.

`lib/logstash-output-<output-name>_jars.rb`
```
require 'jar_dependencies'
require_jar('org.logstash.javaapi', 'logstash-output-java_output_example', '0.0.1')
```
The following items should be modified in the file above:
1. It should be named to correspond with the output name.
1. The `require_jar` directive should be modified to correspond to the `group` specified in the Gradle build file,
the name of the output JAR file, and the version as specified in both the gemspec and Gradle build file.

Once the above files have been properly created along with the plugin JAR file, the gem can be built with the
following command:
```
gem build logstash-output-<output-name>.gemspec
``` 

#### Installing the Java plugin in Logstash

Once your Java plugin has been packaged as a Ruby gem, it can be installed in Logstash with the following command:
```
bin/logstash-plugin install --no-verify --local /path/to/javaPlugin.gem
```
Substitute backslashes for forward slashes as appropriate in the command above for installation on Windows platforms. 

### Feedback

If you have any feedback on Java plugin support in Logstash, please comment on our 
[main Github issue](https://github.com/elastic/logstash/issues/9215) or post in the 
[Logstash forum](https://discuss.elastic.co/c/logstash).