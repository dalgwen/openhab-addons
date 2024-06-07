import org.openhab.automation.java223.scriptsupport.Script;

public class UseLib extends Script {

    @org.openhab.automation.java223.annotations.Library
    MyLib mylib;

    @Override
    public Object onLoad() {
        mylib.sayHello();
        MyLib.sayStaticHello();
        return null;
    }
}
