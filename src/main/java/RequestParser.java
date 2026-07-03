import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class RequestParser {
    private String filename;

    public RequestParser(String filename) {
        this.filename = filename;
    }

   // parse X
    public Map<String, Set<String>> parseXML() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(filename));
            doc.getDocumentElement().normalize(); 

            Map<String, Set<String>> retmap = new HashMap<>();

            final NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                final Element item = (Element) items.item(i);

                // parse out body, decode base64
                final String body = new String(Base64.getDecoder().decode(getChildText(item, "request")), StandardCharsets.ISO_8859_1);

                // Split body into lines
                String[] lines = body.split("\\r?\\n");
                if (lines.length == 0) {
                    continue;
                }
                // First line for method, path (with URL params), protocol
                String firstLine = lines[0].trim();
                String[] firstLineParts = firstLine.split(" ");
                if (firstLineParts.length < 2) {
                    continue;
                }
                String path = firstLineParts[1];

                // Parse URL parameters
                int qIdx = path.indexOf('?');
                if (qIdx >= 0 && qIdx + 1 < path.length()) {
                    String queryString = path.substring(qIdx + 1);
                    String[] params = queryString.split("&");
                    for (String param : params) {
                        String[] kv = param.split("=", 2);
                        String key = kv.length > 0 ? kv[0] : "";
                        String value = kv.length > 1 ? kv[1] : "";
                        if (!key.isEmpty()) {
                            retmap.computeIfAbsent(key, k -> new HashSet<>()).add(value);
                        }
                    }
                }

                // Separate headers from body
                int emptyLineIdx = -1;
                for (int l = 1; l < lines.length; l++) {
                    if (lines[l].trim().isEmpty()) {
                        emptyLineIdx = l;
                        break;
                    }
                }
                // Parse body parameters
                if (emptyLineIdx != -1 && emptyLineIdx + 1 < lines.length) {
                    // Reconstruct body (may span multiple lines)
                    StringBuilder bodyBuilder = new StringBuilder();
                    for (int l = emptyLineIdx + 1; l < lines.length; l++) {
                        if (bodyBuilder.length() > 0) {
                            bodyBuilder.append("\n");
                        }
                        bodyBuilder.append(lines[l]);
                    }
                    String requestBody = bodyBuilder.toString().trim();
                    // Only parse if looks like k=v (not empty)
                    if (!requestBody.isEmpty() && requestBody.contains("=")) {
                        String[] bodyParams = requestBody.split("&");
                        for (String param : bodyParams) {
                            String[] kv = param.split("=", 2);
                            String key = kv.length > 0 ? kv[0] : "";
                            String value = kv.length > 1 ? kv[1] : "";
                            if (!key.isEmpty()) {
                                retmap.computeIfAbsent(key, k -> new HashSet<>()).add(value);
                            }
                        }
                    }
                }
            }

            return retmap;
        }
        catch (Exception e) {
            System.err.println(e);
            return null;
        }
    }

    private static String getChildText(Element parent, String tagName) {
        final NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() == 0) {
            return "";
        }
        return nodes.item(0).getTextContent().trim();
    }


}