# Scim Notification Hub
Provides middle point between publishers and subscribers when managing the scim event notifications.
Implementation of the specification https://tools.ietf.org/html/draft-hunt-scim-notify-00.
## REST API
- deployed on https://perun-dev.meta.zcu.cz/scim-notification

### Get subscription
**GET** `/Subscriptions/{identifier}` <br/>
Retrieve current settings of the subscription.
- Parameters: 
  * identifier - identifier of the subscriber (subscription)
- Returns: subscription details


### Create subscription
**POST** `/Subscriptions` <br/>
Create a new subscription. The body of the request must follow the schema 'urn:ietf:params:scim:schemas:notify:2.0:Subscription'.
- Returns:
  * status 201 and subscription identifier together with the URI location
  * 400 if the subscription json is not valid

### Delete subscription
**DELETE** `/Subscriptions/{identifier}` <br/>
Remove the subscription (also the subscriber in this case).
- Parameters:
  * sbscId - subscription identifier
- Returns:
status 200 or 404 if not found

### Create Scim
**POST** `/Events` <br/>
Create a new scim event notification. The body of the request must follow the schema 'urn:ietf:params:scim:schemas:notify:2.0:Event'.
- Returns:
  * status 204
  * status 400 if the event json is not valid

### Poll
**GET** `/Poll/{identifier}` <br/>
Perform poll of the messages for the specified subscription.
- Parameters:
  * identifier - subscription identifier
- Returns:
status 200

## Feeds
- feed is a queue of events, which waits until all subscribers receive all events before deleting them
- there is no need to explicitly create a feed becase it will be implicitly created when posting event to a new feed or subscribing to a new feed
- Perun feed terminology is following: https://perun-dev.meta.zcu.cz/scim-notification/feed/[id of facility]/[id of service]

## Example Subscription
- modes available:
  * urn:ietf:params:scimnotify:api:messages:2.0:poll
  * urn:ietf:params:scimnotify:api:messages:2.0:webCallback
```
{
  "schemas":
    ["urn:ietf:params:scim:schemas:notify:2.0:Subscription"],
  "feedUri":"https://perun-dev.meta.zcu.cz/scim-notification/feed/25/47",
  "mode":"urn:ietf:params:scimnotify:api:messages:2.0:poll",
  "eventUri":"https://subscriber.com/Events"
}
```

## Example Event
```
{
  "schemas": [
    "urn:ietf:params:scim:schemas:notify:2.0:Event"
  ],
  "publisherUri": "https://perun.cesnet.cz",
  "feedUris": [
    "https://perun-dev.meta.zcu.cz/scim-notification/feed/25/47",
    "https://perun-dev.meta.zcu.cz/scim-notification/feed/23/95"
  ],
  "resourceUris": [
    "https://perun.cesnet.cz/api/v2/Users/3105",
    "https://perun.cesnet.cz/api/v2/Groups/654"
  ],
  "type": "MODIFY",
  "attributes": [
    "id",
    "name",
    "userName",
    "password",
    "emails"
  ],
  "values": {
    "emails": [
      {
        "type": "work",
        "value": "jdoe@example.com"
      }
    ],
    "password": "not4u2no",
    "userName": "jdoe",
    "id": "44f6142df96bd6ab61e7521d9",
    "name": {
      "givenName": "John",
      "familyName": "Doe"
    }
  }
}
```
