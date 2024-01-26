package ${HELPER_PACKAGE};

import org.openhab.core.items.Item;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.automation.javascripting.scriptsupport.JavaScriptingException;

<#list itemImports as itemImport>
import ${itemImport};
</#list>

public class Items extends Script {

<#list items as item>
<#if item.getLabel()??>
    /** ${item.getLabel()} */
</#if>
    public static final String ${item.getName()} = "${item.getName()}";

</#list>

<#list items as item>
<#if item.getLabel()??>
    /** $item.getLabel() */
</#if>
    public ${item.getClass().getSimpleName()} ${item.getName()}() {
        if (itemRegistry != null) {
            return (${item.getClass().getCanonicalName()}) itemRegistry.get( ${item.getName()} );
        } else {
            throw new JavaScriptingException("Things class not properly initialized. Use injection with the @Library annotation in your custom script");
        }
    }

</#list>
}