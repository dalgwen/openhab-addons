import org.openhab.automation.java223.annotations.Library;
import org.openhab.automation.java223.scriptsupport.Script;

@Library
public class MyLib extends Script {

    public static void sayStaticHello() {
        logger.info("Static Hello word");
    }

    public void sayHello() {
        logger.info("Hello word");
    }
}