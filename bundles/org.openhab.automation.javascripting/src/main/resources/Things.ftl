package ${HELPER_PACKAGE};

import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.Thing;

import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.automation.javascripting.scriptsupport.JavaScriptingException;

public class Things extends Script {
 
 
 
<#list things as thing>
<#if thing.getLabel()??>
    /** ${thing.getLabel()} */
</#if>
    public static final String ${escapeName(thing.getUID())} = "${thing.getUID()}";

</#list>

<#list things as thing>
<#if thing.getLabel()??>
    /** ${thing.getLabel()} */
</#if>
    public Thing ${escapeName(thing.getUID())}() {
        if (things != null) {
            return things.get(new ThingUID("${thing.getUID()}"));
        } else {
            throw new JavaScriptingException("Things class not properly initialized. Use injection with the @Library annotation in your custom script");
        }
        
    }

</#list>

}