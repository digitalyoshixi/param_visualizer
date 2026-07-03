import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import burp.api.montoya.http.message.params.ParsedHttpParameter;
import burp.api.montoya.http.message.requests.HttpRequest;

public class RequestParser {
    private String filename;
    public Map<String, Set<String>> mapped_requests;

    public RequestParser(String filename) {
        this.filename = filename;
        this.mapped_requests = new HashMap<>();
    }
    
    public RequestParser() {
        this.mapped_requests = new HashMap<>();
    }


    public void parse_proxy_http(HttpRequest req){
        this.parse_req_string(req.bodyToString());
        for (ParsedHttpParameter param : req.parameters()){
            String key = param.name();
            String value = param.value();
            if (!key.isEmpty()) {
                mapped_requests.computeIfAbsent(key, k -> new HashSet<>()).add(value);
            }
        }
    }

    public void parse_req_string(String body){
        // Split body into lines
        String[] lines = body.split("\\r?\\n");
        if (lines.length == 0) {
            return;
        }
        // First line for method, path (with URL params), protocol
        String firstLine = lines[0].trim();
        String[] firstLineParts = firstLine.split(" ");
        if (firstLineParts.length < 2) {
            return;
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
                    mapped_requests.computeIfAbsent(key, k -> new HashSet<>()).add(value);
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
                        mapped_requests.computeIfAbsent(key, k -> new HashSet<>()).add(value);
                    }
                }
            }
        }

    }

   // parse XML
    public void parseXML() {
        try {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(new File(filename));
            doc.getDocumentElement().normalize(); 

            final NodeList items = doc.getElementsByTagName("item");

            for (int i = 0; i < items.getLength(); i++) {
                final Element item = (Element) items.item(i);
                
                // parse out body, decode base64
                final String body = new String(Base64.getDecoder().decode(getChildText(item, "request")), StandardCharsets.ISO_8859_1);
                parse_req_string(body);
            }

        }
        catch (Exception e) {
            System.err.println(e);
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