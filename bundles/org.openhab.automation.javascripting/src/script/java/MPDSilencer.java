
import java.util.Map;

import org.openhab.automation.javascripting.annotations.ItemStateUpdateTrigger;
import org.openhab.automation.javascripting.annotations.Rule;
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.automation.Action;
import org.openhab.core.automation.module.script.rulesupport.shared.simple.SimpleRule;
import org.openhab.core.items.Item;
import org.openhab.core.library.types.OnOffType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MPDSilencer extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.mpdrules");

    private OnOffType mpd_previous_stop_state;

    @Rule(name = "PhoneRingingRule")
    @ItemStateUpdateTrigger(id = "PhoneRingingTrigger", item = "PhoneRinging")
    public SimpleRule phoneRingingRule = new SimpleRule() {

        @Override
        public Object execute(Action module, Map<String, ?> inputs) {

            logger.info("phone", "phone ringing {}", inputs.get("state"));

            Item mpd_stop = itemRegistry.get("mpd_music_player_pi_stop");

            OnOffType mpd_stop_state = (OnOffType) mpd_stop.getState();

            mpd_previous_stop_state = mpd_stop_state;

            if (mpd_stop_state == OnOffType.OFF) {
                events.sendCommand("mpd_music_player_pi_stop", "ON");
            }

            return "";
        }
    };

    @Rule(name = "PhoneIdleRule")
    @ItemStateUpdateTrigger(id = "PhoneIdleTrigger", item = "PhoneIdle")
    public SimpleRule phoneIdleRule = new SimpleRule() {

        @Override
        public Object execute(Action module, Map<String, ?> inputs) {

            logger.info("phone", "phone idle {}", inputs.get("state"));

            if (mpd_previous_stop_state == OnOffType.OFF) {
                events.sendCommand("mpd_music_player_pi_stop", "OFF");
            }

            return "";
        }
    };

    @Override
    protected Object onLoad() {
        logger.info("phone", "rules loaded");
        return null;
    };
}
