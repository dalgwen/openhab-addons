# openHAB Java223 Scripting

Write OpenHAB scripts in Java as a JSR223 language.

Features :

- full JSR 223 support (use in files, in GUI, transformations, inline rule action, etc...)
- auto injection of OpenHAB variable/preset for simplicity
- library support for sharing code (.jar and .java)
- rule annotations available in the helper library for creating rules the easiest way
- helper library files auto generation for items, things, and actions, with strong typing and ease of use
- cache compiled scripts in memory for blazingly fast executions after the first one (sub millisecond overload)
- no boilerplate code for simple script: you can do a one liner script, as declaring a class and a method is optional.
- optional reuse of instances script to share values between execution occurrences
- designed to be easily used with your favorite IDE

It makes heavy use of Eric Obermühlner's Java JSR 223 ScriptEngine [java-scriptengine](https://github.com/eobermuhlner/java-scriptengine), and is partially based on work from other OpenHAB contributors that create their own JSR 223 java automation bundle (many thanks to them).

# What you can do ?

All JSR223 OpenHAB related thing, and surely a bit more, thanks to your scripts sharing the same JVM as OpenHAB.
If you just want to see how to use it, see the [Examples](#examples) section.

# How it works

You should first take a look at the [official documentation about OpenHAB JSR223 support](https://www.openhab.org/docs/configuration/jsr223.html).
That said, keep reading for useful insider informations.

## Script location: where can I use Java223 ?

### First location option: GUI

As a full featured JSR223 automation bundle, you can use the GUI to use Java223 scripts, everywhere JSR223 scripts are allowed. Including, but not limited to:

- Creating `Scripts` in the so-called GUI section
- Inside a `Rule`, as an inline script action in the `Then` or the `Only If` section
- When linking a channel to an item, as a transformation `Profile` of type `Script Java`

### Second location option: File script

A JSR223 script file is a script located in your configuration directory, under the `automation/jsr223` sub directory.

At startup, or each time a file is created (or modified) in this directory, OpenHAB will handle it to the relevant JSR223 scripting language for **immediate** execution (using the extension as a discriminating value). So in our case, every `.java` files will be handled by the Java223 automation bundle.

As a script can create and register rules during its execution (by accessing and using the OpenHAB automation manager), **this 'file mode' is then especially useful for defining rules**. And icing on the cake: when a script that created rules is deleted, the linked rules are also deleted, thanks to the way OpenHAB registers a rule (same for modification, the associated rules are deleted and recreated). See the [rules](#rules) section for more information on how to create a rule.

## Execution

A Java223 script does not need to have any dependency to anything in order to be compiled and executed. It can just be a plain, simple java class like this one:

```java
public class SimpleClass {
    public void main() {
        int sum = 2 + 2;
    }
}
```

(In fact it can even be a simpler one-liner, see the [no boilerplate section](#noboilerplate)

When OpenHAB presents a java script to the Java223 automation bundle, it searches for methods with name like `main`, or `eval`, or `run`, or `exec`, or any methods annotated with `@RunScript` and then runs them (from here we will refer to those as the "runnable methods"). Returning a value is supported but optional.  That's all you need for a very simple script !

A note about the context : each script has its own context, its own ClassLoader. It means that scripts are perfectly separated, and cannot interact with, or even see, each other. But do not worry, because there are dedicated features for this ([shared cache](#sharedcache) for sharing values, [library](#library) for sharing code).

## Variable injection

Of course, a script needs to communicate with OpenHAB to be useful. We will call 'OpenHAB inputs' those objects, values, references, that OpenHAB gives to the automation bundles, in order for it to expose them to user. For example, a reference to the items registry will allow a script to interact with items by checking their state or giving them command.

With this Java223 bundle, it is done by the way of automatic injection. It means that you don't need to do anything special. You just have to declare variable in your script and the bundle will take care of injecting the corresponding value in it. There are three input injection possibilities:

- as a field in your script (see [example](#fieldinjection))
- as a method parameter in your runnable methods (see [example](#parameterinjection))
- as a method parameter in the constructor of the script. (see [example](#constructorinjection))

The variable name is used to find the correct value to inject, so take care of your spelling (full reference in [official documentation about OpenHAB JSR223 support](https://www.openhab.org/docs/configuration/jsr223.html#scriptextension-objects-all-jsr223-languages) ), or inherit the [Java223Script helper class](#java223script) to directly have the right variable names.

### Advanced injection

You can control the injection further (i.e. overriding default behavior, or directly injecting something from a preset) with the @InjectBinding annotation. See [example](#injectbinding).

<a id="rules"></a>

## Defining Rules

As a JSR223 OpenHAB language, you can define rule with the OpenHAB DSL. All needed classes and instance (SimpleRule, TriggerBuilder, automationManager instance, etc.) are of course exposed natively. You can see an example of how to use it [here](https://www.openhab.org/docs/configuration/jsr223.html#example-rules-for-a-first-impression) (examples are written with other languages, but concepts and objects for Java223 are the same).

**However**, keep in mind that there is a much, much more convenient way to do this. You can jump to the relevant section [here](#helperrules). But the following sections also exposes some prerequisites if you want to have a better comprehension before jumping in.

<a id="library"></a>

## Library for sharing code

To share reusable code between your scripts, you have to define a library. A library is a .java file (or a .jar archive containing several compiled class) located in your configuration directory, under the `automation\lib\java` subdirectory.

The Java223 bundle will monitor this directory, and automatically adds everything inside to the compilation unit of your script (although, it's not applied retrospectively). The script still have its dedicated ClassLoader, but inside this ClassLoader, all your library classes are also available.

Be careful : it also means that other scripts have their own library classes inside their own ClassLoader. **You cannot share value between scripts this way**, even by using a static property inside a library class.

### Auto injection of library

Your library probably also needs to communicate with OpenHAB. You can of course pass OpenHAB input references as a parameter to your library methods, or by a setter. For example, if we imagine a library `MyLibrary` that need access to the items and things registries :

```java
...
    ItemRegistry ir; // <- auto injected in your script
    ThingRegistry things; // <- auto injected in your script
    public void main() {
        var myUsefulParameter1 = ...
        var myUsefulParameter2 = ...
        MyLibrary myLib = new MyLibrary();
        myLib.doSomethingInteresting(ir, things, myUsefulParameter1, myUsefulParameter2); // <- then pass 'ir' and 'things' for your library to use
    }
...
```

**But**, as you can see, it can be cumbersome and unnecessarily hard to read. This Java223 bundle provides a much simpler way to do this : letting it instantiate your library and auto inject all OpenHAB inputs value into them. It works on field, or method/constructor parameter.  See [example](#libraryautoinjection). It can even works recursively : a lib can reference another lib, itself referencing some OpenHAB inputs, and all this will work out of the box.

Getting back to our example : As the library instantiation and injection with the items and things registries are taken care of, the same code can then become :

```java
    public void main(MyLibrary myLib) { // <- myLib will be instantiated by the bundle, and auto injected with the OpenHAB input variables declared in it.
        var myUsefulParameter1 = ...
        var myUsefulParameter2 = ...
        myLib.doSomethingInteresting(myUsefulParameter1, myUsefulParameter2); // <-- myLib already have registries reference injected, I do not need to pass them here
    }
```

Tip: The Java223 automation bundle recognizes a library by its type, so you don't have to worry about respecting a naming convention for the variable. Feel free to use anything.


## Generated helper library

The helper library is totally optional, but you should seriously consider using it, as it will make your code experience much more streamlined. It consists of two parts: dynamically .java generated files, and a JAR file with some already compiled class.

### Java dynamic classes

The Java223 bundle generates some ready-to-use libraries in the `automation\lib\java` directory. These classes are dynamic and contains information about your OpenHAB setup.

You will get several java files in the package `helper.generated` :

- Items.java : contains all your items name as static String, and label as their javadoc. Also contains methods to directly get the Item, casted to the right Class. (see [example](#itemsandthings))
- Things.java : contains all your Thing UID as static String, with label as their javadoc. Also contains methods to directly get the Thing. (see [example](#itemsandthings))
- Actions.java : contains strongly typed, ready to use methods, to get the actions available on your things. (see [example](#actions))
<a id="java223script"></a>
- Java223Script.java : this abstract class will come **very** handy. In fact, it is so handy that all your scripts should inherit it ! It already contains all OpenHAB inputs variables, as well as some others useful shortcut. Take a look at it.

As these files are no more no less standard library files, you can of course use them as candidates for auto injection in your script. Be careful though, do not use the variable names `items`, `things`, or `actions`, as they are already reserved as OpenHAB input values for the ItemRegistry, ThingsRegistry, and ScriptThingActions respectively.
As a reference, in the super handy Java223Script helper abstract class, we are using `_items`, `_things`, `_actions` for them.

**Tip : all your scripts, *including libraries*, can extend the `Java223Script` class. This way they will automatically obtain easy access to all OpenHAB inputs, to some shortcuts, etc.**

<a id="helperrules"></a>

### helper-lib.jar and rules

**This is the most useful feature of this entire bundle.**

The Java223 bundle also copies in your `automation\lib\java` a pre-compiled jar with a set of library files inside. This jar is no more, no less, a standard library jar, and is an example of how powerful the OpenHAB JSR223 feature is. It contains all you need to define Rules with the help of simple-to-use annotations. The entry point is the `RuleAnnotationParser` class. The `parse` method automatically scans your script, searching for annotated method defining rules, and then creates and registers them.

Tip : The best way to use this functionality is to extend the `Java223Script`, as it already contains a call to the `parse` method in a `@RunScript` annotated method.

When combined with all the aforementioned helpers, see how easy it is to define a rule.

```java
import ...;

public class MyRule extends Java223Script {

    @Rule
    @ItemStateUpdateTrigger(itemName = Items.my_detector_item, state = OnOffType.ON.toString())
    public void myRule() {
        _items.my_bulb_item.send(OnOffType.ON);
    }
}
```

This rule above is triggered by a 'ON' state update of an item linked to a detector, and then light a bulb : **Here really shines the JSR223 for Java : no random strings, full auto completion from your IDE, strongly typed code.**

You can also use automatic injection **in your rule method parameter**. It is especially useful for strongly typed parameter. Take a look at this rule, triggered by two different detectors:

```java
import ...;

public class MyRule extends Java223Script {

    @Rule(name = "detecting.people", description = "Detecting people and light")
    @ItemStateUpdateTrigger(itemName = Items.my_detector_item, state = OnOffType.ON.toString())
    @ItemStateUpdateTrigger(itemName = Items.my_otherdetector_item, state = OnOffType.ON.toString())
    public void myRule(ItemStateChange inputs) { // HERE, strongly typed parameter
        _items.my_bulb_item.send(OnOffType.ON);
        logger.info("Movement detected at " + inputs.getItemName()); // inputs.getItemName() give me the triggering detector name
    }
}
```

`ItemStateChange` is available in the helper-lib.jar, alongside other strongly typed events. As it is a Java223 library class like others, it leverages the autoinjection feature: its fields are automatically injected with the corresponding parameter given by OpenHAB. So, by using the right event object for your trigger, such as `ItemStateChange` in this example, you don't have to check the documentation to search for how the event parameter you need is named, and you won't miss the parameter because you misspelled it. You should find in the helper lib the other event objects matching the triggers of your rules.

Here are all functionalities of the helper-lib:

- Many different `@Trigger` class. Check the `helper.rules.annotation` package for a list.
- You can add (multiple) `@Condition` to a Rule. It exposes a pre-condition for the rule to execute. Check the `helper.rules.annotation` package
- `@Trigger`, `@Conditions`, `@Rule` have many parameter. Some parameters add functionality, others can overwrite default behavior (for example using the method name for the label of a rule).
- Pre-made event objects that you can use as a parameter in a rule are defined in the package `helper.rules.eventinfo`. You can define your own if some are missing (do not hesitate to make a Pull Request)
- If you want all the triggering event input parameters in a map for a rule, you can use the parameter `Map<String, ?> inputs`.
- You can set the `@Rule` annotation on a method, but also on many type of field containing code to execute, such as Function, Runnable... Take a look at the class `Java223Rule`. You can even switch the value of the field at runtime, thus making the code your rule execute even more dynamic.

<a id="sharedcache"></a>

## Share value between scripts

To share value between different scripts, you can use the shared cache available in the `cache` preset. Auto-inject it with :

```java
    protected @InjectBinding(preset = "cache", named = "sharedCache") ValueCache sharedCache;
```

This cache is accessible the same way a `Map<String, Object>` is.

Tip : it is automatically available to scripts inheriting the Java223Script helper class.

## Share value between script executions

The Java223 automation bundle has an option `allowInstanceReuse`. If set to true, the default engine behavior will be to reuse script instance between executions, instead of re-instantiating with a `new` operator every time. If you run the same script over and over, it will try to use the same instance, thus allowing you to store information in its field (in memory, so only for the duration of the OpenHAB process). Be careful for read/write concurrency issue.

Of course your script has to remain the same. So script file in the `automation/jsr223` directory cannot use this functionality, as they are only executed once by nature, when OpenHAB start, or when they are created or modified (which is another way of saying deleted/recreated).

You can also overwrite this default behavior for individual script by using the `@ReuseScriptInstance` annotation on the class level.

Take note that it uses the compilation cache. So if your cache is not big enough (50 script by default), persistence of your fields values is not assured.

You should also note that Rule inner working is different: your rule method code is always executed on the same instance, no matter the value of `allowInstanceReuse` or the presence of a `@ReuseScriptInstance` configuration. So you can also share information here, as a field in classes defining Rule.

<a id="noboilerplate"></a>

## No boilerplate code

Sometimes, you 'real' (useful) code is very short, and you don't need complex logic, custom auto injection, etc.
In this case, you can omit the 'boilerplate' code, and just write your 'useful' code.
Under the hood, the Java223 bundle will 'wrap' your code inside a class inheriting `Java223Script`, with a bunch of standard import (mainly item state types) and a main method.

For example, this one-line script is perfectly valid:

```java
    _items.myitem().send(OFF); // let there be light
```

This 'wrapping' will take place if nowhere in your code a trimmed line starts with `public class`.

If you need to import some class, you can also do it. The import statements (lines starting with `import `) will be parsed and added in the beginning of the resulting script, before the wrapping class and method.

You can return a value. The line returning the value MUST begins with `return `. This is useful for Transformation.

But because your code is wrapped, the following functionalities are not available:

- definition of methods (your code is already inside one)
- customize the auto-injection (class field members or method parameters, are not available for addition/modification). You have to rely on what is already injected by the parent Java223Script.


## Transformation

You can use Java223 script in transformation.
A transformation is a piece of code with an input and an output. So you just have to respect this contract:

- you can use the OpenHAB input value named 'input'. Auto injection is possible. Or you can inherit the Java223Script, as it is already declared.
- your runnable method must return a value

Example of transformation appending the word "Hello" to the input, using the "no boilerplate" functionality:

```java
return "Hello " + input.toString();
```

# Use your IDE

## convenience-dependencies.jar

A jar file, purely for convenience, is exported from the classpath and added to the lib directory when the bundle starts. This jar is EXCLUDED from entering the compilation unit of your script; it sole purpose is for you to use inside an IDE. It contains most of the OpenHAB classes you probably need to write and compile scripts and rules. By using this jar in your project, you probably won't have to setup advanced dependencies management tools such as Maven or Gradle.

You can ask the Java223 bundle to add to this jar some classes by using the following Java223 configuration properties:

- `additionalBundles`: Additional package name exposed by bundles running in OpenHAB. Use ',' as a separator.
- `additionalClasses`: Additional individual classes. Use ',' as a separator.

## Configure your project

In order to use an IDE and write code with autocompletion, javadoc, and all other syntaxic sugar, you just have to add to your project :

- As a source directory: the root directory of your scripts, under `automation/jsr223` (probably `automation/jsr223/java`, but you can use what you want)
- As a source directory: the root directory of the library `automation/lib/java`
- As a library: `automation/lib/java/helper-lib.jar`
- As a library: `automation/lib/java/convenience-dependencies.jar`

Tip : to access a remote OpenHAB installation, you can copy, use Webdav, a Samba share, a SFTP virtual file system or sync feature (available on your OS or included in your IDE), or any other mean you can think about.

<a id="examples"></a>

# Configuration parameters

| Parameter Name                 | Type    | Default | Label                           | Description |
|--------------------------------|---------|---------|---------------------------------|-------------|
| `scriptCacheSize`             | text    | 50      | Script Cache Size              | Script compilation cache size. 0 to disable. A positive number allows keeping compilation result. |
| `allowInstanceReuse`          | boolean | false   | Allow Script Instance Reuse     | Reuse an instance if found in the cache. Allow sharing data between subsequent executions. Note: Beware of concurrency issues. |
| `additionalBundles`           | text    | -       | Additional Bundles              | Additional bundles exported for developing, concatenated by ",". |
| `additionalClasses`           | text    | -       | Additional Classes              | Additional classes exported for developing, concatenated by ",". |
| `stabilityGenerationWaitTime` | integer | 10000   | Stability Generation Wait Time  | Delay (in ms) before writing generated classes. Each new generation triggering event further delays the generation. Useful to prevent multiple code generations when many Things activate at the same time. |
| `startupGuardTime`           | integer | 60000   | Startup Guard Time              | Delay (in ms) before overwriting previously generated classes, at startup. Useful to not replace files from previous openHAB run with incomplete generation from a not fully loaded system. |


# Examples

## Lightning a bulb

```java
import org.openhab.core.library.types.OnOffType;
import helper.generated.Java223Script;

public class BasicExample extends Java223Script {
    public void main() {
        _items.myitem().send(OnOffType.OFF); // let there be light
    }
}
```

<a id="simplerule"></a>


## No boilerplate code

A one liner can also work

```java
    _items.myitem().send(OnOffType.OFF); // let there be light
```


## Create a simple rule

This rule is triggered by a 'ON' state update of an item linked to a detector, and then light a bulb. 

```java
import ...;

public class MyRule extends Java223Script {

    @Rule
    @ItemStateUpdateTrigger(itemName = Items.my_detector_item, state = OnOffType.ON.toString())
    public void myRule() {
        _items.my_bulb_item.send(OnOffType.ON);
    }
}
```

## Create a rule with several trigger and options

This time, the rule is triggered by a 'ON' state update on one of two possible detectors.
The method parameter is a strongly typed library element (`ItemStateChange`) and as such, its field are auto injected with the right value from the input. Thanks to this, it is easy to get the input parameters without risking using a wrong parameter name. For example, we get here the name of the item triggering the detection, for a detailed log.
Instead of the default (the method name used for the label of the rule), it has a description, and a dedicated name for the label, and both will be shown on the OpenHAB GUI.

```java
import ...;

public class MyRule extends Java223Script {

    @Rule(name = "detecting.people", description = "Detecting people and light")
    @ItemStateUpdateTrigger(itemName = Items.my_detector_item, state = OnOffType.ON.toString())
    @ItemStateUpdateTrigger(itemName = Items.my_otherdetector_item, state = OnOffType.ON.toString())
    public void myRule(ItemStateChange inputs) { // here, strongly typed parameter
        _items.my_bulb_item.send(OnOffType.ON);
        logger.info("Movement detected at {}", inputs.getItemName());
    }
}
```

## Example of different injection types of OpenHAB input variables

<a id="fieldinjection"></a>

If you don't want to extend the `Java223Script` class, then you will have to take care of formatting your script for injection of OpenHAB input value.

### Field input injection

```java
import ...;

public class FieldInjectionExample {
    ItemRegistry itemRegistry; // <-- the injection will happen here, 'itemRegistry' is a valid OpenHAB input name
    public void main() {
        itemRegistry.get("myitem").send(OnOffType.ON);;
    }
}
```

<a id="parameterinjection"></a>

### Method parameter input injection

```java
import ...;

public class MethodInjectionExample {
    public void main(ItemRegistry itemRegistry) {  // <-- the injection will happen here, 'itemRegistry' is a valid OpenHAB input name
        itemRegistry.get("myitem").send(OnOffType.ON);;
    }
}
```

<a id="constructorinjection"></a>

### Constructor parameter input injection

```java
import ...;

public class ConstructorInjectionExample {

    ItemRegistry myItemRegistry;  // <-- the injection WON'T happen here because the variable name is not the name as an available OpenHAB input

    public SimpleClass(ItemRegistry itemRegistry) { // <-- the injection will happen here, 'itemRegistry' is a valid OpenHAB input name
        this.myItemRegistry = itemRegistry;
    }

    public void main() {
        myItemRegistry.get("myitem").send(OnOffType.ON);;
    }
}
```

<a id="runrule"></a>

## Run another rule or script

You may want to run another rule or a script. The rule manager is not a standard JSR223 variable, but the Java223 automation bundle can nonetheless inject it.
First, inject the rule manager in your script, then use it with the runNow method and the UID of the rule.

Tip : The ruleManager is already declared as a field in the Java223Script helper class that you can inherit.

```java 
import java.util.Map;
import org.openhab.core.automation.RuleManager;

public class RunAnotherRule {
    public void main(RuleManager ruleManager) {
        // simple execution :
        ruleManager.runNow("myruleid");
        // execution with parameters in a key / value map
        // set the boolean parameter to true if you want to check conditions before execution (in case of a full rule)
        ruleManager.runNow("myparameterizedruleid", false, Map.of("key", "value")))
    }
}
```

## Disable a thing

The ThingManager is not a standard JSR223 variable, but the Java223 automation bundle can nonetheless inject it. It is also available in the base class `Java223Script`, as shown in this example.

```java 
import java.util.Map;
import org.openhab.core.automation.RuleManager;

public class DisableThing extends Java223Script {
    public void main() {
        thingManager.setEnabled(_things.network_pingdevice_mything().getUID(), false);
    }
}
```


## Use metadata

The MetadataRegistry is not a standard JSR223 variable, but the Java223 automation bundle can nonetheless inject it. This script overwrites Google Assistant metadata every time it is executed (so, at each OpenHAB startup), effectively keeping this file as some kind of external 'database' where you can store all your metadata.

```java 
public class MetadataDatabase {

    protected MetadataRegistry metadataRegistry; // <-- auto injection here

    public void main() {
        create("ga", Items.lock, "Lock", Map.of("name", "Front door"));
        create("ga", Items.room_light, "Light", Map.of("name", "Master bedroom light"));
    }
    
    private void create(String namespace, String itemName, String value, Map<String, Object> configuration) {
        MetadataKey metadataKey = new MetadataKey(namespace, itemName);
        metadataRegistry.remove(metadataKey);
        metadataRegistry.add(new Metadata(metadataKey, value, configuration));
    }
}
```

Tip : The metadataRegistry is already declared as a field in the Java223Script helper class that you can inherit.


<a id="injectbinding"></a>

## Advanced injection control

Control automatic injection behavior by using the `@InjectBinding` annotation. You can use it on field or on method/constructor parameter.

```java 
public class InjectBindingExample {

    // inject something from a preset :
    protected @InjectBinding(preset = "RuleSupport", named = "automationManager") ScriptedAutomationManager automationManager;
    // disable injection even if the field name should trigger it :
    protected @InjectBinding(enable = "false") ItemRegistry itemRegistry;
    // name your variable as you wish :
    protected @InjectBinding(named = "itemRegistry") ItemRegistry otherVariableName;
    // make it mandatory (the script will not run if the value cannot be found). Note : mandatory = true is the default value when using the annotation.
    protected @InjectBinding(mandatory = true) ThingRegistry things;
    
    public void main(ItemRegistry itemRegistry) {
        myItemRegistry.get("myitem");
    }
}
```

<a id="libraryautoinjection"></a>

## Library use and auto injection

Inside the `automation/lib/java` directory, let's define a library that will be available to all scripts.

```java
import ...;

public class MyGreatLibrary {
    ItemRegistry itemRegistry; // will be auto-injected if instantiation is taken care of by the bundle

    public void myUsefullLibraryMethod(String itemName) {
        itemRegistry.get(itemName);
        //something usefull...
    }
}
```

Here is how to use it with auto injection your script (in automation/jsr223/), for example with field injection :

```java
import ...;

public class MyScript {

    MyGreatLibrary mylib; // will be auto instantiated and then auto injected with all needed OpenHAB input value

    public void exec() {
        mylib.myUsefullLibraryMethod("myitemName");
    }
}
```

<a id="itemsandthings"></a>

Tip : do not forget that all classes, including libraries, can extend Java223Script

## Items and Things helper libraries

By extending the Java223Script class (optional, you can inject them the way you want), the variable _items and _things are directly accessible.

```java
import ...;

public class ItemsAndThingAccessExample extends Java223Script { // <-- take the Java223Script class as a base class
                                                                // to access _items and _things more easily

    public void exec() {
        _items.myLightItem().send(OnOffType.ON); // <-- light on !
        logger.info(_things.zwave_device_2ecfa3a2_node68().getStatus().toString()); // <-- get thing info
    }
}
```

<a id="actions"></a>

## Actions helper libraries

With the auto generated `Actions` class (here referenced by the `_actions` variable), you can call a method to get strongly typed actions (and auto completion) linked to your Thing.

```java
import ...;

public class ActionExample extends Java223Script { // <-- take the Java223Script class as a base class
                                                   // to access _actions more easily

    public void exec() {
        _actions.getSmsmodem_SMSModemActions(Things.mySMSthing).sendSMS("+3312345678", "Hello world");;
    }
}
```

