import org.openhab.automation.javascripting.scriptsupport.Script;

public class UseLib extends Script {

    @org.openhab.automation.javascripting.annotations.Library
    MyLib mylib;

    @Override
    public Object onLoad() {
        mylib.sayHello();
        MyLib.sayStaticHello();
        return null;
    }
}
