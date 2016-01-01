package com.northernwall.hadrian.webhook;

import com.northernwall.hadrian.parameters.Parameters;
import com.squareup.okhttp.OkHttpClient;

/**
 *
 * @author rthursto
 */
public interface WebHookSenderFactory {
    public WebHookSender create(Parameters parameters, OkHttpClient client);
    
}