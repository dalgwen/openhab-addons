
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.items.Item;
import org.openhab.core.persistence.extensions.PersistenceExtensions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PersistItems extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.persist");

    @Override
    protected Object onLoad() {

        Item item = itemRegistry.get("Morning_Temperature");

        PersistenceExtensions.persist(item);

        logger.info("persist done");
        
        return null;
    }
}
