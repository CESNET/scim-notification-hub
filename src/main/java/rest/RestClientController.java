package rest;


import core.ScimEventNotification;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.Set;

/**
 * Rest client controller.
 *
 * @author Jiri Mauritz
 */
@Named
@Singleton
public class RestClientController {

    public static RestTemplate restTemplate;

    public RestClientController() {
        restTemplate = new RestTemplate();
        SimpleClientHttpRequestFactory rf = (SimpleClientHttpRequestFactory) restTemplate.getRequestFactory();
        rf.setConnectTimeout(1 * 1000);
    }

    public static void webCallback(Set<String> eventUris, ScimEventNotification sen) {
        for (String eventUri : eventUris) {
            try {
                restTemplate.postForEntity(eventUri, sen, ScimEventNotification.class);
            } catch (Exception e) {
                // time out, log and continue
                System.err.println("Error while connecting to the uri " + eventUri + ": " + e.getMessage());
            }
        }
    }

}
