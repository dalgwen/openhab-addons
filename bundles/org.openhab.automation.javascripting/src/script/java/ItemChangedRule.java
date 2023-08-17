
import java.util.Map;

import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.Trigger;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ItemChangedRule extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.itemrule");

    private int counter = 1;

    @Override
    protected Object onLoad() {

        logger.info("Java onLoad()");

        SimpleRule sr = new SimpleRule() {

            @Override
            public Object execute(Action module, Map<String, ?> inputs) {

                logger.info("Java execute {}", counter++);

                return "";
            }
        };

        Trigger trigger = createItemStateChangeTrigger("BatteryLevelChangedTrigger", "BatteryLevel");

        ruleBuilder(sr).withName("BatteryLevelChanged").withTrigger(trigger).activate();

        logger.info("BatteryLevelChanged rule activated");
        
        return null;
    };
}
