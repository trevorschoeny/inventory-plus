# Checks

Plain-Java self-checks for Inventory Plus logic that needs no game. No mod has a test source set yet, so they live here.

- `ReachCheck.java`: the reach record (`lockgroups/Reach`): defaults by kind, entries, new groups copying their kind's default, orphan resolution, cycling, JSON round trip, and the MenuKit 6.0.0 key migrations (5.x slot group keys and created-slot keys). Run after compiling Inventory Plus:

Reach lives in Inventory Plus's client source set since it went universal, and reads MenuKit, so the classpath is the client runtime one. Gradle prints it with a one-line init script:

```
echo "allprojects { p -> p.afterEvaluate { if (p.name == 'inventory-plus') p.tasks.register('printClientCp') { doLast { println 'CP=' + p.sourceSets.client.runtimeClasspath.asPath } } } }" > /tmp/printcp.gradle
CP=$(./gradlew -q -I /tmp/printcp.gradle :inventory-plus:printClientCp | sed -n 's/^CP=//p')
javac -cp "$CP" -d /tmp/rc ReachCheck.java && java -cp "$CP:/tmp/rc" ReachCheck
```
