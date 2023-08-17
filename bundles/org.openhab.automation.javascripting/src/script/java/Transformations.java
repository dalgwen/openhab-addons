
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.transform.actions.Transformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Transformations extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javarules.transform");

    @Override
    protected Object onLoad() {

        String s = Transformation.transform("REGEX", ".*(hello).*", "hello, world");

        logger.info("transform done, got: " + s);
        
        return null;
    }
}
