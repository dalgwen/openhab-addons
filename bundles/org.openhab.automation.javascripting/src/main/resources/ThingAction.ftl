package ${packageName};

import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.thing.binding.ThingActions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

<#list classesToImport as classToImport>
<#if classToImport?has_content>
import ${classToImport};
</#if>
</#list>

public class ${simpleClassName} {

    protected static Logger logger = LoggerFactory.getLogger(${simpleClassName}.class);
    
    public static final String SCOPE = "${scope}";
    
    ThingActions thingActions;
    
    public ${simpleClassName}(ScriptThingActions thingActions, String thingUID) {
        this.thingActions = thingActions.get(SCOPE, thingUID);
    }
    
    public ${simpleClassName}(ThingActions thingActions) {
        this.thingActions = thingActions;
    }
    
<#list methods as method>
    @SuppressWarnings("unchecked")
    public ${method.returnValueType()} ${method.name()}(<#list method.parameterTypes() as parameter>${parameter} p${parameter?counter}<#sep>,</#list>) {
        try {
            Class<?> thingActionClass = thingActions.getClass();
            Method method = thingActionClass.getMethod("${method.name()}");
<#if (method.parameterTypes()?size > 0)>
            <#if (method.returnValueType() != "void")>@SuppressWarnings("unused")
            Object returnValue = </#if>method.invoke(thingActions, <#list 1..method.parameterTypes()?size as i>p${i}<#sep>,</#list>);
<#else>
            <#if (method.returnValueType() != "void")>@SuppressWarnings("unused")
            Object returnValue = </#if>method.invoke(thingActions);
</#if>
<#if (method.returnValueType() != "void")>
            return (${method.returnValueType()}) returnValue;
</#if>            
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            logger.error("Error running action {}", "${method.name()}", e);
        }
<#if (method.returnValueType() != "void")>
        return null;
</#if>  
    }

</#list>

}