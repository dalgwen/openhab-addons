package ${HELPER_PACKAGE};

import java.util.Map;
import org.openhab.automation.javascripting.scriptsupport.JavaScriptingException;
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

    public static void setBindings(Map<String, ?> bindings) {
        Items.itemRegistry = (ItemRegistry) bindings.get("itemRegistry");
    }

<#list items as item>
<#if item.getLabel()??>
    /** $item.getLabel() */
</#if>
    public static ${item.getClass().getSimpleName()} ${item.getName()}() {
        return (${item.getClass().getSimpleName()}) getItem(${item.getName()});
    }

</#list>
    protected static Item getItem(String itemId) {
        if (itemRegistry != null) {
            return itemRegistry.get(NewItem);
        } else {
            throw new JavaScriptingException("Items class not properly initialized");
        }
    }
}