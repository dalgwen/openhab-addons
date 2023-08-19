package mypackage;

import org.openhab.automation.javascripting.scriptsupport.Script;
import org.openhab.automation.javascripting.annotations.Library;

@Library
public class MyLib extends Script {

    public static void sayStaticHello() {
        logger.info("Static Hello word");
    }
    
    public void sayHello() {
        logger.info("Hello word"); 
    }    
}