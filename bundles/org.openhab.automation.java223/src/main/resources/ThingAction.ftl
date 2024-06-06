package ${packageName};

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.automation.java223.helper.Logger;
import org.openhab.automation.java223.helper.Java223Exception;
 
<#list classesToImport as classToImport>
<#if classToImport?has_content>
import ${classToImport};
</#if>
</#list>

public class ${simpleClassName} {

    protected static Logger logger = Logger.getLogger(${simpleClassName}.class);
    
    public static final String SCOPE = "${scope}";
    
    private static ScriptThingActions scriptThingActions;
    ThingActions thingActions;
    
    public ${simpleClassName}(String thingUID) {
        if (scriptThingActions == null) {
            throw new Java223Exception("ScriptThingActions is null, maybe you tried to instantiate"
                    + " this class too soon ? Avoid instantiating this class in static code,"
                    + " instantiate it in a constructor instead");
        }
        thingActions = scriptThingActions.get(SCOPE, thingUID);
    }

<#list methods as method>
    public ${method.returnValueType()} ${method.name()}(<#list method.parameterTypes() as parameter>${parameter} p${parameter?counter}<#sep>, </#list>) {
        try {
            Class<?> thingActionClass = thingActions.getClass();
<#if (method.parameterTypes()?size > 0)>
            Method method = thingActionClass.getMethod("${method.name()}", <#list method.parameterTypes() as parameter>${parameter}.class<#sep>, </#list>);
            <#if (method.returnValueType() != "void")>@SuppressWarnings("unused")
            Object returnValue = </#if>method.invoke(thingActions, <#list 1..method.parameterTypes()?size as i>p${i}<#sep>,</#list>);
<#else>
            Method method = thingActionClass.getMethod("${method.name()}");
            <#if (method.returnValueType() != "void")>@SuppressWarnings("unused")
            Object returnValue = </#if>method.invoke(thingActions);
</#if>
<#if (method.returnValueType() != "void")>
            return (${method.returnValueType()}) returnValue;
</#if>            
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            throw new Java223Exception("Error running action ${method.name()}", e);
        }
    }

</#list>
}