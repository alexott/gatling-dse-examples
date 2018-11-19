# gatling-dse-examples

Examples of using gatling-dse-plugin & gatling-dse-stress.

# Example for gatling-dse-plugin

gatling-dse-plugin is described in [following article](http://alexott.net/files/gatling-dse-plugin/gatling-dse-plugin.html).

Building example is very simple - just change to the `plugin-sim` directory and execute `mvn package` there - this will create the jar that contains all code.

Besides this, you also need to initialize schema using the command `cqlsh -f cql/create-schema.cql` - this will create 2 tables in the `gatling` keyspace.

# Example for gatling-dse-stress

to be written...
