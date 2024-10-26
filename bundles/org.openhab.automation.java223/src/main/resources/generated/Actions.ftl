package ${packageName};

import java.lang.reflect.Method;

import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;
import org.openhab.core.thing.binding.ThingActions;

<#list classesToImport as classToImport>
<#if classToImport?has_content>
import ${classToImport};
</#if>
</#list>

public class Actions {

    @InjectBinding
    private ScriptThingActions actions;

    public Actions(ScriptThingActions actions) {
        this.actions = actions;
    }
    
    /**
     * Helper method to call arbitrary action. You should use the dedicated generated static actions class if possible
     *
     * @param thingActions
     * @param method
     * @param params
     */
    public static void invokeAction(ThingActions thingActions, String method, Object... params) {
        Class<?>[] paramClasses = new Class<?>[params.length];

        for (int i = 0; i < params.length; i++) {
            paramClasses[i] = params[i].getClass();
        }
        try {
            Method m = thingActions.getClass().getMethod(method, paramClasses);
            m.invoke(thingActions, params);
        } catch (Exception e) {
            throw new Java223Exception("Cannot invoke action for " + method);
        }
    }

<#list actionsByScope as scope, actions>
<#list actions as action>
    public ${lastName(action)} get${camelCase(scope)}_${lastName(action)}(String thingUID) {
        return new ${lastName(action)}(actions, thingUID);
    }
</#list>
</#list>
}
