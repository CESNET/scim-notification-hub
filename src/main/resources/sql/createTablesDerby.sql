CREATE TABLE scim_subscriber (
  id         BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  identifier VARCHAR(512) NOT NULL UNIQUE
);

CREATE TABLE scim_feed (
  id                    BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  uri                   VARCHAR(2083) NOT NULL UNIQUE,
  slowest_subscriber_id BIGINT REFERENCES scim_subscriber (id)
);

CREATE TABLE scim_event_notification (
  id            BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  publisher_uri VARCHAR(2083) NOT NULL,
  type          VARCHAR(64)   NOT NULL,
  sen_values    CLOB          NOT NULL
);

CREATE TABLE scim_subscription (
  id            BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  mode          VARCHAR(64)   NOT NULL,
  event_uri     VARCHAR(2083) NOT NULL,
  last_seen_msg BIGINT REFERENCES scim_event_notification (id),
  subscriber_id BIGINT        NOT NULL REFERENCES scim_subscriber (id)
    ON DELETE CASCADE,
  feed_id       BIGINT        NOT NULL REFERENCES scim_feed (id)
    ON DELETE CASCADE
);

CREATE TABLE scim_feed_sen (
  feed_id     BIGINT NOT NULL REFERENCES scim_feed (id),
  sen_id      BIGINT NOT NULL REFERENCES scim_event_notification (id),
  prev_msg_id BIGINT REFERENCES scim_event_notification (id),
  PRIMARY KEY (feed_id, sen_id)
);

CREATE TABLE scim_sen_attribute (
  name   VARCHAR(512) NOT NULL,
  sen_id BIGINT       NOT NULL REFERENCES scim_event_notification (id)
    ON DELETE CASCADE
);

CREATE TABLE scim_sen_resource_uri (
  uri    VARCHAR(512) NOT NULL,
  sen_Id BIGINT       NOT NULL REFERENCES scim_event_notification (id)
    ON DELETE CASCADE
);

CREATE TABLE scim_sen_schema (
  name   VARCHAR(512) NOT NULL,
  sen_id BIGINT       NOT NULL REFERENCES scim_event_notification (id)
    ON DELETE CASCADE
);
