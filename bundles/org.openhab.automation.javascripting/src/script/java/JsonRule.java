
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.openhab.automation.javascripting.annotations.ItemStateUpdateTrigger;
import org.openhab.automation.javascripting.annotations.Rule;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.DecimalType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

public class JsonRule extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.jsonrule");

    final String outsideTemperatureItem = "OutsideTemperature";
    final String salonTemperatureItem = "SalonTemperature";

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Rule(name = "JsonRule")
    @ItemStateUpdateTrigger(id = "OutsideTemperatureTrigger", item = outsideTemperatureItem)
    @ItemStateUpdateTrigger(id = "SalonTemperatureTrigger", item = salonTemperatureItem)
    public SimpleRule jsonrule = new SimpleRule() {

        @Override
        public Object execute(Action module, Map<String, ?> inputs) {

            logger.info("Java jsonrule execute: {}", inputs.toString());

            try {
                String json = createJson();

                logger.info("json: {}", json);

                Item item = itemRegistry.get("EPaper_Screen_Json");

                events.sendCommand(item, json);
            } catch (Exception e) {
                logger.error(inputs.toString(), e);
                throw e;
            }

            return "";
        }
    };

    @Override
    protected Object onLoad() {
        logger.info("Java onLoad()");
        return null;
    };
    

    private String createJson() {
        Map<String, List<Map<String, Object>>> screen = new HashMap<>();

        List<Map<String, Object>> ops = new ArrayList<>();

        int x = 10;
        int y = 0;

        LocalDateTime ldt = LocalDateTime.now();
        String s = ldt.format(dateTimeFormatter);

        Map<String, Object> text0 = new HashMap<>();
        text0.put("type", "text");
        text0.put("x", x);
        text0.put("y", y);
        text0.put("text", String.format("@ %s", s));

        ops.add(text0);

        y += 30;

        DecimalType dt = itemRegistry.get(outsideTemperatureItem).getStateAs(DecimalType.class);

        if (dt != null) {
            float f = dt.floatValue();

            Map<String, Object> text1 = new HashMap<>();
            text1.put("type", "text");
            text1.put("x", x);
            text1.put("y", y);
            text1.put("text", String.format("Temp Outside: %.1f", f));

            ops.add(text1);

            y += 30;
        }

        DecimalType dts = itemRegistry.get(salonTemperatureItem).getStateAs(DecimalType.class);

        if (dts != null) {
            float f = dts.floatValue();

            Map<String, Object> text1 = new HashMap<>();
            text1.put("type", "text");
            text1.put("x", x);
            text1.put("y", y);
            text1.put("text", String.format("Temp Salon: %.1f", f));

            ops.add(text1);

            y += 30;
        }

        screen.put("screenobjects", ops);

        Gson gson = new Gson();

        String output = gson.toJson(screen);

        return output;
    }
}
