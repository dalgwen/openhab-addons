package ${packageName};

import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.core.automation.module.script.defaultscope.ScriptThingActions;

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

<#list actionsByScope as scope, actions>
    public ${camelCase(scope)} ${scope} = new ${camelCase(scope)}();
    public class ${camelCase(scope)} {
<#list actions as action>
        public ${lastName(action)} get${lastName(action)}(String thingUID) {
            return new ${lastName(action)}(actions, thingUID);
        }
</#list>
    }
</#list>
}
