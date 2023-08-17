
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Map;

import org.openhab.automation.javascripting.annotations.ItemStateUpdateTrigger;
import org.openhab.automation.javascripting.annotations.Rule;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.items.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileWriteRule extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.writefile");

    @Rule(name = "TemperatureToFileRule")
    @ItemStateUpdateTrigger(id = "TemperatureTrigger", item = "Morning_Temperature")
    public SimpleRule temperatureToFileRule = new SimpleRule() {

        @Override
        public Object execute(Action module, Map<String, ?> inputs) {

            logger.info("@ItemStateUpdateTrigger Morning_Temperature");

            Item item = itemRegistry.get("Morning_Temperature");

            Number state = (Number) item.getState();

            Path path = Paths.get("/tmp/Morning_Temperature.txt");

            try {
                Files.writeString(path, String.format("%f%n", state.floatValue()), StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (IOException e) {
                logger.error("", e);
                throw new RuntimeException(e);
            }

            return "";
        }
    };

    @Override
    protected Object onLoad() {
        logger.info("Java onLoad()");
        return null;
    }
}
