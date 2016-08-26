package rest;


import core.*;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.inject.Inject;
import java.io.IOException;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Map;
import java.util.Set;

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

    @Inject
    private Environment env;

    private SecureRandom random = new SecureRandom();

    private static final String WEB_CALLBACK = "urn:ietf:params:scimnotify:api:messages:2.0:webCallback";
    private static final String POLL = "urn:ietf:params:scimnotify:api:messages:2.0:poll";


    /**
     * GET /
     * Root request for verifying deployment.
     *
     * @return info message
     */
    @RequestMapping(value = "/", method = GET)
    public ResponseEntity<String> root() {
        return new ResponseEntity<>("Scim Notification Hub version 1.0", HttpStatus.OK);
    }

    /**
     * GET /Subscriptions/{identifier}
     * Retrieve current settings of the subscription.
     *
     * @param sbscId identifier of the subscriber (subscription)
     * @return subscription details
     */
    @RequestMapping(value = "/Subscriptions/{sbscId}", method = GET)
    public ResponseEntity<Subscription> getSubscription(@PathVariable("sbscId") String sbscId) {
        Subscriber subscriber = manager.getSubscriberByIdentifier(sbscId);
        if (subscriber.getSubscriptions().isEmpty()) {
            manager.removeSubscriber(subscriber.getIdentifier());
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        Subscription subscription = subscriber.getSubscriptions().iterator().next();

        return new ResponseEntity<>(subscription, HttpStatus.OK);
    }

    /**
     * POST /Subscriptions
     * Create a new subscription.
     * The body of the request must follow the schema 'urn:ietf:params:scim:schemas:notify:2.0:Subscription'.
     *
     * @param body of the request containing the subscription
     * @return status 201 or 400 if the subscription json is not valid
     */
    @RequestMapping(value = "/Subscriptions", method = POST)
    public ResponseEntity<String> createSubscription(@RequestBody String body) {
        ObjectMapper mapper = new ObjectMapper();
        String sbscId;
        try {
            Map<String, Object> json = (Map<String, Object>) mapper.readValue(body, Map.class);
            String feedUri = (String) json.get("feedUri");
            String modeString = (String) json.get("mode");
            SubscriptionModeEnum mode;
            if (POLL.equals(modeString)) {
                mode = SubscriptionModeEnum.poll;
            } else if (WEB_CALLBACK.equals(modeString)) {
                mode = SubscriptionModeEnum.webCallback;
            } else {
                throw new IOException("Wrong subscription mode.");
            }
            String eventUri = (String) json.get("eventUri");

            // generate subscription id
            sbscId = nextSubscriptionId();

            manager.newSubscription(sbscId, feedUri, mode, eventUri);
        } catch (IOException | ClassCastException e) {
            e.printStackTrace();
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        String response = "Location: \n" + env.getProperty("webProperties.hostName") + "/Subscriptions/" + sbscId + "\n" + body;
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * DELETE /Subscriptions/{identifier}
     * Remove the subscription (also the subscriber in this case).
     *
     * @param sbscId subscription identifier
     * @return status 200 or 404 if not found
     */
    @RequestMapping(value = "/Subscriptions/{sbscId}", method = DELETE)
    public ResponseEntity<String> deleteSubscription(@PathVariable("sbscId") String sbscId) {
        try {
            boolean deleted = manager.removeSubscriber(sbscId);
            if (deleted) {
                return new ResponseEntity<>(HttpStatus.OK);
            } else {
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);
            }
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
    }


    /**
     * POST /Events
     * Create a new scim event notification.
     *
     * @param senJson scim event notification according to the schema 'urn:ietf:params:scim:schemas:notify:2.0:Event'
     * @return status 204 or status 400 if the event json is not valid
     */
    @RequestMapping(value = "/Events", method = POST)
    public ResponseEntity<?> createScimEvent(@RequestBody String senJson) {
        try {
            manager.newMessage(senJson);
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    /**
     * GET /Poll/{identifier}
     * Perform poll of the messages for the specified subscription.
     *
     * @param sbscId subscription identifier
     * @return status 200
     */
    @RequestMapping(value = "/Poll/{sbscId}", method = GET)
    public ResponseEntity<?> poll(@PathVariable("sbscId") String sbscId) {
        Set<ScimEventNotification> msgs;
        try {
            msgs = manager.poll(sbscId);
        } catch (IllegalArgumentException e) {
            return new ResponseEntity<>(e.getMessage(), HttpStatus.BAD_REQUEST); // TODO: add exception message to the response
        }
        return new ResponseEntity<>(msgs, HttpStatus.OK);
    }

    private String nextSubscriptionId() {
        String identifier = new BigInteger(130, random).toString(25);
        Set<String> alreadyCreatedIdentifiers = manager.getSubscriberIdentifiers();
        while (alreadyCreatedIdentifiers.contains(identifier)) {
            identifier = new BigInteger(130, random).toString(25);
        }
        return identifier;
    }
}
