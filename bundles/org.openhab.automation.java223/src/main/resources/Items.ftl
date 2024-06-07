package ${packageName};

import java.util.Map;
import org.openhab.automation.java223.helper.Java223Exception;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;

<#list itemImports as itemImport>
import ${itemImport};
</#list>

public class Items {

    private static ItemRegistry itemRegistry;

<#list items as item>
<#if item.getLabel()??>
    /** ${item.getLabel()} */
</#if>
    public static final String ${item.getName()} = "${item.getName()}";

</#list>

<#list items as item>
<#if item.getLabel()??>
    /** ${item.getLabel()} */
</#if>
    public static ${item.getClass().getSimpleName()} ${item.getName()}() {
        return (${item.getClass().getSimpleName()}) getItem(${item.getName()});
    }
</#list>
    
    protected static Item getItem(String itemId) {
        if (itemRegistry != null) {
            return itemRegistry.get(itemId);
        }
        throw new Java223Exception("Items class not properly initialized");
    }
}