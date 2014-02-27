package dzserver;

import java.io.File;

import play.Play;
import play.mvc.Controller;

public class Application {
    public static File applicationHomeDir() {
        return Play.application().path();
    }

    public static String queryString(String name) {
        return Controller.request().getQueryString(name);
    }

    public static String flashMessage(String name) {
        return Controller.flash(name);
    }

    public static String staticConfigString(String key) {
        return Play.application().configuration().getString(key);
    }

    public static String[] createCouterKeys(String product, String position) {
        return new String[] { product, product + "_" + position };
    }
}
