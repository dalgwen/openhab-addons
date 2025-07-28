package ${packageName};

import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;

public class Things {
  
    @InjectBinding
    public ThingRegistry things;
    
<#list things as thing>
<#if thing.getLabel()??>
    /** ${thing.getLabel()} */
</#if>
    @InjectBinding(enable = false)
    public static final String ${escapeName(thing.getUID())} = "${thing.getUID()}";

</#list>

<#list things as thing>
<#if thing.getLabel()??>
    /** ${thing.getLabel()} */
</#if>
    public Thing ${escapeName(thing.getUID())}() {
        return getThing("${thing.getUID()}");
    }
</#list>
    
    protected Thing getThing(String stringUID) {
        if (things != null) {
            return things.get(new ThingUID(stringUID));
        } else {
            throw new Java223Exception("Things class not properly initialized. Use automatic instanciation by injection");
        }
    }

}