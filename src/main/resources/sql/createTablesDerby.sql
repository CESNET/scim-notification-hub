CREATE TABLE subscriber (
  id         BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  identifier VARCHAR(512) NOT NULL
);

CREATE TABLE feed (
  id                  BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  uri                 VARCHAR(2083) NOT NULL UNIQUE,
  slowestSubscriberId BIGINT        NOT NULL REFERENCES subscriber (id)
);

CREATE TABLE subscription (
  id           BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  mode         VARCHAR(64)   NOT NULL,
  eventUri     VARCHAR(2083) NOT NULL,
  subscriberId BIGINT        NOT NULL REFERENCES subscriber (id),
  feedId      BIGINT        NOT NULL REFERENCES feed (id)
);

CREATE TABLE scim_event_notification (
  id           BIGINT        NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  publisherUri VARCHAR(2083) NOT NULL,
  type         VARCHAR(64)   NOT NULL,
  sen_values   CLOB          NOT NULL
);

CREATE TABLE feed_sen (
  feedUri BIGINT NOT NULL REFERENCES feed (id),
  senId   BIGINT NOT NULL REFERENCES scim_event_notification (id)
);

CREATE TABLE sen_attribute (
  id    BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  name  VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);

CREATE TABLE sen_resourceUri (
  id    BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  uri   VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);

CREATE TABLE sen_schema (
  id    BIGINT       NOT NULL PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  name  VARCHAR(512) NOT NULL,
  senId BIGINT       NOT NULL REFERENCES scim_event_notification (id)
);
