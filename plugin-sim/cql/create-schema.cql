CREATE KEYSPACE IF NOT EXISTS gatling WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

drop table if exists oauth_tokens;
drop table if exists user_tokens;

CREATE TABLE oauth_tokens (
 id uuid,
 nonce int,
 user text,
 created timestamp,
 expires timestamp,
 attributes frozen<map<text, text>>,
 
 PRIMARY KEY(id)
);

CREATE TABLE user_tokens (
 user text,
 id uuid,
 nonce int,
 created timestamp,
 expires timestamp,
 attributes frozen<map<text, text>>,

 PRIMARY KEY(user, id)
);
