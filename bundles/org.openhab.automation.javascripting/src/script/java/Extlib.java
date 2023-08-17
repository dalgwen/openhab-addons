
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Extlib extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.ext");

    @Override
    protected Object onLoad() {

        String s = "";

        // commented out, haven't got the dependency to .ext here
        // s = org.openhab.automation.javarules.ext.T.ID;

        logger.info("ext done, got: " + s);
        
        return s;
    }
}
