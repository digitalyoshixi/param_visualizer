import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;
import burp.api.montoya.proxy.Proxy;
import burp.api.montoya.http.message.requests.HttpRequest;
import burp.api.montoya.proxy.ProxyHttpRequestResponse;

public class Extension implements BurpExtension {
    @Override
    public void initialize(MontoyaApi montoyaApi) {
        montoyaApi.extension().setName("Parameter Collector");

        // Parser Logic
        RequestParser parser = new RequestParser();
        RequestHandler handler = new RequestHandler(parser);
        
        // Listen in on burp History
        montoyaApi.proxy().registerRequestHandler(handler);

        // Extract all previous burp history
        Proxy proxy = montoyaApi.proxy();
        List<ProxyHttpRequestResponse> history = proxy.history();
        for (ProxyHttpRequestResponse reqresp : history) {
            HttpRequest req = reqresp.request();
            // montoyaApi.logging().logToOutput(req.parameters());
            parser.parse_proxy_http(req);
        }

        // Build UI panel for the extension's tab
        JPanel panel = new JPanel();
        panel.setLayout(new java.awt.BorderLayout(10, 10));
        panel.add(new JLabel(" -- Parameter Collector -- "), java.awt.BorderLayout.NORTH);

        // need to add table of param -> values

        // need to add text box + button to convert all params into url-passable form param1=1&param2=2&...

        // need to make UI update callback function from the proxy listener

        montoyaApi.userInterface().registerSuiteTab("Parameter Collector", panel);
    }
}