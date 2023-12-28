#set( $H2 = '##' )

# openHAB Java Scripting

This openHAB add-on provides support for JSR 223 scripts written in Java that can be used as rules or transformations.

It makes heavy use of Eric Obermühlner's Java JSR 223 ScriptEngine [java-scriptengine](https://github.com/eobermuhlner/java-scriptengine).

# Advantages of Programming Scripts in Java

* high level language Java 

* use the power of the Java runtime library

* develop in your favorite IDE

* remote-debug scripts

* scripts run in Java's speed, after JVM warm-up in native code speed

# Programming Hints

* all Java classes used as JSR 223 script have to inherit from [org.openhab.automation.javascripting.scriptsupport.Script](src/main/java/org/openhab/automation/javascripting/scriptsupport/Script.java)

* When the openHAB ScriptFileWatcher detects a new .java File in conf/automation/jsr223 
  it is loaded, compiled into memory and its onLoad() method is executed.
  Then it is parsed for @Rule annotations and the rules are activated.
  
* You can use a raw script with no "boilerplate" code and direct instructions by NOT specifiying a class in your script (not putting a `public class` declaration). Your script will automatically be wrapped in a Class and an onLoad method. You obviously cannot declare any method within this raw script (because it is itself contained in the onLoad method), but you can use import or package declarations (they will be extracted and put in the  start of the resulting script).

* Java script classes do not see other script classes. Each one has its own ClassLoader. This is a consequence of the way openHAB JSR223 and the Java ScriptEngine works, each script is loaded separately and so has its own memory ClassLoader. You can use the Library annotation to circumvent this limitation : each script will still have its own ClassLoader, but all @Library annotated classes will also be compiled with each of them.

* you can also use libraries if you package them as [OSGI bundles](#library-code).

* you can use openHAB classes from the packages listed in [bnd.bnd](bnd.bnd).

* openHAB Java Scripting requires openHAB 3.3.0 or later

# Remote Debugging

start openHAB with start_debug.sh and remote debug from Eclipse, stop at breakpoints.

![screenshot](src/doc/images/EclipseDebug.png?raw=true)
 

# Test

* Copy org.openhab.automation.javascripting-VERSION.jar into the addons folder (download via the [Releases](https://github.com/weberjn/org.openhab.automation.javascripting/releases) link).

* Copy from the sample Java classes into conf/automation/jsr223/

(they are all in src/script/java)

A Java class is loaded, compiled into memory and its onLoad() method executed.
A Java script will not work as a Python or JS Script (which is evaluated during load). For java this is simulated with the onLoad() method. So, rules can be defined programmatically in onLoad().

Or, you can annotate public instance variables of type SimpleRule. See the FileWriteRule sample. You can also directly annotate methods of the Script. See the CronRule sample.

# Project for Scripts

To have a script compile without errors in Eclipse, it should be in a Java project with openHAB dependencies and a dependency to javascripting.

* create a folder with a Maven project:
* use this [src/doc/pom.xml](src/doc/pom.xml) as template 
* adapt parent relativePath
* change groupId and artifactId
* import the folder as maven project into Eclipse
* create the Java scripts in src/main/java in the default package 
* if the source compiles without errors, copy it to conf/automation/jsr223

```sh
mvn  -DskipChecks clean install
```


# Library Code 

${H2} 1st method : Bundle

Java Rules has `DynamicImport-Package: *` so it can access code in other bundles. 

Bundle your code as OSGI bundle as in this sample: https://github.com/weberjn/org.openhab.automation.javascripting.ext 

${H2} 2nd method : Library annotation

You can also put java files in the `conf/automation/lib/java` directory. By doing so, these libraries will be available to all your java scripts. The library class can still extends the `Script` base class to access its facilities.

You can then use it normally, or you can even inject it in your script by using the annotation `@org.openhab.automation.javascripting.annotations.Library` on a class member. This injection also allows the library to be instanciated by openHAB and to use all the `Script` facilities, such as access to registries, etc.

Be aware that a library instance is not shared between scripts. If you want to share data you should find another way.

# Building the Addon

Clone [java-scriptengine](https://github.com/eobermuhlner/java-scriptengine) and mvn install (symlink ch.obermuhlner.scriptengine.java/src to make the Maven
build work).

Clone Java Scripting under openhab-addons/bundles and run mvn install

# Sample Scripts

The samples are all in [src/script/java](src/script/java).

${H2} Item change rules, annotation based

```java
#include("src/script/java/MPDSilencer.java")
```

${H2} Changing Items

```java
#include("src/script/java/EventBusExamples.java")
```

${H2} Cron Rule, method annotation based

```java
#include("src/script/java/CronRule.java")
```

${H2} ItemChanged Rule

```java
#include("src/script/java/ItemChangedRule.java")
```

${H2} Transformation Script in Java

a sitemap referencing a transformation in Java

```java
#include("src/doc/sitemaps/timetranform.sitemap")
```

and the transformation used (it must be in conf/transform)

```java
#include("src/script/java/SecHHMMSSTransformation.java")
```

${H2} Addon Actions

```java
#include("src/script/java/SendMail.java")
```

${H2} Static Actions

```java
#include("src/script/java/StaticActions.java")
```

${H2} Transformations

```java
#include("src/script/java/Transformations.java")
```

${H2} Persistence

```java
#include("src/script/java/PersistItems.java")
```

${H2} Write to a File

```java
#include("src/script/java/FileWriteRule.java")
```

Set a new temperature

```Shell
openhab> openhab:update Morning_Temperature 37.7
```

```Shell
$ cat /tmp/Morning_Temperature.txt
37.700001
```

${H2} Json Rule

This rule is triggered by either of two items, creates a Json String from their states and sends it to a third item 
(which should be linked to an MQTT command topic, on which a Python script could listen and feed an e-paper display).

```java
#include("src/script/java/JsonRule.java")
```

```Shell  
openhab> openhab:update  OutsideTemperature 27
Update has been sent successfully.
openhab> openhab:status EPaper_Screen_Json
{"screenobjects":[{"x":10,"y":0,"text":"@ 2023-06-04 20:07:42","type":"text"},{"x":10,"y":30,"text":"Temp Outside: 27.0","type":"text"}]}
```
  
${H2} Groovy Port

This class is ported from the [openHAB JSR 223 Groovy Sample](https://www.openhab.org/docs/configuration/jsr223.html#groovy).
It does not use syntactic sugar of the Script base class, only pure openHAB JSR 223.

```java
#include("src/script/java/GroovyPort.java")
```

${H2} Library example

Define a library class :

```java
#include("src/script/java/MyLib.java")
```

Use it in another script, either in a static way or with an injection :

```java
#include("src/script/java/UseLib.java")
```

${H2} Raw script

You can use a raw script to avoid writing boilerplate code.
Within it, you can use import, package declaration.
You can return a value (optional).

```java
#include("src/script/java/RawScript.jav")
```

