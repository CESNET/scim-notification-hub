CREATE TABLE subscriber (
  id         BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  identifier VARCHAR(512) NOT NULL
);

CREATE TABLE feed (
  id                  BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  uri                 VARCHAR(2083) NOT NULL UNIQUE,
  slowestSubscriberId BIGINT REFERENCES subscriber (id)
);

CREATE TABLE scim_event_notification (
  id           BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  publisherUri VARCHAR(2083) NOT NULL,
  type         VARCHAR(64)   NOT NULL,
  sen_values   CLOB          NOT NULL
);

CREATE TABLE subscription (
  id           BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  mode         VARCHAR(64)   NOT NULL,
  eventUri     VARCHAR(2083) NOT NULL,
  lastSeenMsg  BIGINT REFERENCES scim_event_notification (id),
  subscriberId BIGINT        NOT NULL REFERENCES subscriber (id),
  feedId       BIGINT        NOT NULL REFERENCES feed (id)
);

CREATE TABLE feed_sen (
  feedId  BIGINT NOT NULL REFERENCES feed (id),
  senId   BIGINT NOT NULL REFERENCES scim_event_notification (id),
  nextMsg BIGINT REFERENCES scim_event_notification (id),
  PRIMARY KEY (feedId, senId)
);

CREATE TABLE sen_attribute (
  name  VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);

CREATE TABLE sen_resource_uri (
  uri   VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);

CREATE TABLE sen_schema (
  name  VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);
