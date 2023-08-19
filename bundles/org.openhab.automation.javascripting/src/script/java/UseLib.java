import org.openhab.automation.javascripting.scriptsupport.Script;

import java.util.Map;
import mypackage.MyLib;

public class UseLib extends Script {

    @org.openhab.automation.javascripting.annotations.Library
    MyLib mylib;

    public Object onLoad() {
        mylib.sayHello();
        MyLib.sayStaticHello();
        return null;
    }
}
