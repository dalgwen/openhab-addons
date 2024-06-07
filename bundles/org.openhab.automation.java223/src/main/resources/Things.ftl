package ${packageName};

import java.util.Map;
import org.openhab.automation.java223.helper.Java223Exception;
import org.openhab.core.thing.ThingUID;
import org.openhab.core.thing.Thing;
import org.openhab.core.thing.ThingRegistry;

public class Things {
  
    public static ThingRegistry things;
    
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
    public static Thing ${escapeName(thing.getUID())}() {
        return getThing("${thing.getUID()}");
    }
</#list>
    
    protected static Thing getThing(String stringUID) {
        if (things != null) {
            return things.get(new ThingUID(stringUID));
        } else {
            throw new Java223Exception("Things class not properly initialized");
        }
    }

}