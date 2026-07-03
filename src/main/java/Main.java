import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;

public class Main {

    public static void main(String[] args) {
        String file = args.length > 0 ? args[0] : "reqs";
        RequestParser parser = new RequestParser(file);
        parser.parseXML();
        Map<String, Set<String>> params = parser.mapped_requests;
        if (params == null) {
            return;
        }
        params.forEach((name, values) ->
                System.out.println(name + " -> " + values.size() + " value(s): " + values));
    }
}
