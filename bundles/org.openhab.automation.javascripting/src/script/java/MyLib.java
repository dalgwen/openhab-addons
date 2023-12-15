import org.openhab.automation.javascripting.annotations.Library;
import org.openhab.automation.javascripting.scriptsupport.Script;

@Library
public class MyLib extends Script {

    public static void sayStaticHello() {
        logger.info("Static Hello word");
    }

    public void sayHello() {
        logger.info("Hello word");
    }
}