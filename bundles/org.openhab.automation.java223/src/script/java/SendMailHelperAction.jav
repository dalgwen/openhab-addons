
import org.openhab.automation.java223.scriptsupport.Script;
import org.openhab.core.thing.binding.ThingActions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SendMailHelperAction extends Script {

    private Logger logger = LoggerFactory.getLogger("org.openhab.automation.java223.mail");

    @Override
    protected Object onLoad() {

        MailSendMailActions sendMailAction = new MailSendMailActions(actions, "mail:smtp:mailSender");
        sendMailAction.sendMail("mail_at_receiver", "a subject", "mailcontent Java script onload()");

        logger.info("mail sent");
        
        return null;
    }
}
