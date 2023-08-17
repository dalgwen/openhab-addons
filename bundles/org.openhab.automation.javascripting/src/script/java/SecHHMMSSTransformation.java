import java.time.Duration;

import org.openhab.automation.javascripting.scriptsupport.Script;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SecHHMMSSTransformation extends Script {

	private Logger logger = LoggerFactory.getLogger("org.openhab.automation.javascripting.TR");
	
	@Override
	protected Object onLoad() {
		
		logger.info("Java onLoad()");
		
		String s = (String)input;
		
		Duration d = null;
		
		try {
			d = Duration.ofSeconds(Long.parseLong(s));
		} catch (NumberFormatException e) {
			return null;
		}

		String timeHHMMSS = String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());

		logger.info( "duration: {}", timeHHMMSS);
		
		return timeHHMMSS;

	}

}
