
import java.util.Map;

import org.openhab.automation.java223.annotations.CronTrigger;
import org.openhab.automation.java223.annotations.Rule;
import org.openhab.automation.java223.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CronRule extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.java223.cronrule");

    private int counter = 1;

    @Rule(name = "CronRule")
    @CronTrigger(id = "CronTrigger", cronExpression = "0 * * * * ?")
    public Object execute(Map<String, ?> inputs) {

        logger.info("Java cronrule execute {}", counter++);

        return "";
    }

    @Override
    protected Object onLoad() {
        logger.info("Java onLoad()");
        return null;
    };
}
