package rest;


import core.Manager;
import core.SubscriptionModeEnum;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;

import java.io.IOException;
import java.util.Map;

import static org.springframework.web.bind.annotation.RequestMethod.*;

/**
 * Rest controller of the manager class and all application.
 *
 * @author Jiri Mauritz
 */
@RestController
public class RestApiController {

    @Inject
    private Manager manager;


    // for now, we dont have any tool to get subscriber id, therefore there is just one
    public static final String SBSC_ID = "id";
    public static final String WEB_CALLBACK = "urn:ietf:params:scimnotify:api:messages:2.0:webCallback";
    public static final String POLL = "urn:ietf:params:scimnotify:api:messages:2.0:poll";


    /**
     * POST /Subscription
     * Create a new subscription.
     * The body of the request must follow the schema 'urn:ietf:params:scim:schemas:notify:2.0:Subscription'.
     *
     * @param body
     * @return
     */
    @RequestMapping(value = "/Subscription", method = POST)
    public ResponseEntity<String> subscription(@RequestBody String body) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Map<String, Object> json = (Map<String, Object>) mapper.readValue(body, Map.class);
            String feedUri = (String) json.get("feedUri");
            String modeString = (String) json.get("mode");
            SubscriptionModeEnum mode;
            if (POLL.equals(modeString)) {
                mode = SubscriptionModeEnum.poll;
            } else {
                mode = SubscriptionModeEnum.webCallback;
            }
            String eventUri = (String) json.get("eventUri");
            manager.newSubscription(SBSC_ID, feedUri, mode, eventUri);
        } catch (IOException | ClassCastException e) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>("", HttpStatus.CREATED);
    }

    /**
     * POST /{feedUri}/Events
     * Creates a new scim event notification.
     * The feedUri is useless for now, because the sen is classified according to the feed uris attribute of the message.
     *
     * @param feedUri identification of the feed in URL, but not used for now
     * @param senJson scim event notification according to the schema 'urn:ietf:params:scim:schemas:notify:2.0:Event'
     * @return status 204
     */
    @RequestMapping(value = "/{feedUri}/Events", method = POST)
    public ResponseEntity<?> subscriber(@PathVariable("feedUri") String feedUri, @RequestBody String senJson) {
        manager.newMessage(senJson);
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }
}
