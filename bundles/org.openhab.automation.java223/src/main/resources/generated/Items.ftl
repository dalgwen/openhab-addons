package ${packageName};

import org.openhab.automation.java223.common.InjectBinding;
import org.openhab.automation.java223.common.Java223Exception;
import org.openhab.core.items.Item;
import org.openhab.core.items.ItemRegistry;

<#list itemImports as itemImport>
import ${itemImport};
</#list>

public class Items {

    @InjectBinding
    private ItemRegistry itemRegistry;

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
    public ${item.getClass().getSimpleName()} ${item.getName()}() {
        return (${item.getClass().getSimpleName()}) getItem(${item.getName()});
    }
</#list>
    
    protected Item getItem(String itemId) {
        if (itemRegistry != null) {
            return itemRegistry.get(itemId);
        }
        throw new Java223Exception("Items class not properly initialized. Use automatic instanciation by injection.");
    }
}