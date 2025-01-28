package ${packageName};

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.thing.binding.ThingActions;
import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Exception;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
 
<#list classesToImport as classToImport>
<#if classToImport?has_content>
import ${classToImport};
</#if>
</#list>

@InjectBinding(enable = false)
public class ${simpleClassName} {

    protected static Logger logger = LoggerFactory.getLogger(${simpleClassName}.class);
    
    public static final String SCOPE = "${scope}";
    
    ThingActions thingActions;
    ScriptThingActions scriptThingActions;
    String thingUID;
    
    public ${simpleClassName}(ScriptThingActions actions, String thingUID) {
        this.thingActions = actions.get(SCOPE, thingUID);
        this.scriptThingActions = actions;
        this.thingUID = thingUID;
    }

<#list methods as method><#if (method.returnValueType() != "void")>
    @SuppressWarnings("unchecked")</#if>
    public ${method.returnValueType()} ${method.name()}(<#list method.parameterTypes() as parameter>${parameter} p${parameter?counter}<#sep>, </#list>) {
        if (thingActions == null) {
            thingActions = scriptThingActions.get(SCOPE, thingUID);
        }
        
        try {
            Class<?> thingActionClass = thingActions.getClass();
<#if (method.parameterTypes()?size > 0)>
            Method method = thingActionClass.getMethod("${method.name()}", <#list method.nonGenericParameterTypes() as parameter>${parameter}.class<#sep>, </#list>);
            <#if (method.returnValueType() != "void")>
            Object returnValue = </#if>method.invoke(thingActions, <#list 1..method.parameterTypes()?size as i>p${i}<#sep>, </#list>);
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