
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.model.script.actions.Exec;
import org.openhab.core.model.script.actions.HTTP;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StaticActions extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javarules.actions");

    @Override
    protected Object onLoad() {

        String res = HTTP.sendHttpGetRequest("http://localhost/");

        String cmd = "termux-media-player play camera-shutter.mp3";
        Exec.executeCommandLine("ssh", "nexus9", cmd);

        logger.info("static actions done");
        
        return null;
    }
}
