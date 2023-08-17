
import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.core.thing.binding.ThingActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendMail extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.mail");

    @Override
    protected Object onLoad() {

        ThingActions thingActions = actions.get("mail", "mail:smtp:mailSender");
        actions.invoke(thingActions, "mail_at_receiver", "a subject", "mailcontent Java script onload()");

        logger.info("mail sent");
        
        return null;
    }
}
