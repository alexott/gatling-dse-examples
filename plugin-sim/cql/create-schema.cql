CREATE KEYSPACE IF NOT EXISTS gatling WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1};

use gatling;

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
) with compaction = {'tombstone_compaction_interval':8640, 'class': 'LeveledCompactionStrategy'}
  and gc_grace_seconds = 3600;

CREATE TABLE user_tokens (
 user text,
 id uuid,
 nonce int,
 created timestamp,
 expires timestamp,
 attributes frozen<map<text, text>>,

 PRIMARY KEY(user, id)
) with compaction = {'tombstone_compaction_interval':8640, 'class': 'LeveledCompactionStrategy'}
  and gc_grace_seconds = 3600;
