import burp.api.montoya.proxy.http.InterceptedRequest;
import burp.api.montoya.proxy.http.ProxyRequestHandler;
import burp.api.montoya.proxy.http.ProxyRequestReceivedAction;
import burp.api.montoya.proxy.http.ProxyRequestToBeSentAction;

public class RequestHandler implements ProxyRequestHandler {
    private RequestParser parser;

    public RequestHandler(RequestParser parser) {
        this.parser = parser;
    }

    @Override
    public ProxyRequestReceivedAction handleRequestReceived(InterceptedRequest interceptedRequest) {
        // No-op: let the request flow through unchanged
        return ProxyRequestReceivedAction.continueWith(interceptedRequest);
    }

    @Override
    public ProxyRequestToBeSentAction handleRequestToBeSent(InterceptedRequest interceptedRequest) {
        parser.parse_proxy_http(interceptedRequest);
        return ProxyRequestToBeSentAction.continueWith(interceptedRequest);
    }
}