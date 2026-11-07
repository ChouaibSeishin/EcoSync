CREATE TABLE chat_conversation (
 organization_id uuid NOT NULL REFERENCES organization(id), id uuid NOT NULL,
 owner_id uuid NOT NULL REFERENCES app_user(id), title varchar(100) NOT NULL,
 status varchar(16) NOT NULL DEFAULT 'OPEN' CHECK(status IN('OPEN','ARCHIVED')),
 next_sequence integer NOT NULL DEFAULT 1 CHECK(next_sequence BETWEEN 1 AND 201),
 created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,id,owner_id)
);
CREATE INDEX chat_owner_recent ON chat_conversation(organization_id,owner_id,updated_at DESC);
CREATE TABLE chat_turn (
 organization_id uuid NOT NULL, id uuid NOT NULL, conversation_id uuid NOT NULL, owner_id uuid NOT NULL,
 request_id uuid NOT NULL, input_hash char(64) NOT NULL, status varchar(32) NOT NULL,
 progress varchar(100) NOT NULL, error_code varchar(64), provider varchar(32) NOT NULL,
 model varchar(160) NOT NULL, prompt_version varchar(16) NOT NULL,
 tool_calls integer NOT NULL DEFAULT 0 CHECK(tool_calls BETWEEN 0 AND 8),
 dependencies jsonb NOT NULL DEFAULT '[]', provenance jsonb NOT NULL DEFAULT '[]',
 started_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, ended_at timestamptz,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,conversation_id,id),
 UNIQUE(organization_id,owner_id,request_id),
 FOREIGN KEY(organization_id,conversation_id,owner_id) REFERENCES chat_conversation(organization_id,id,owner_id),
 CHECK(status IN('RUNNING','COMPLETE','PARTIAL','FAILED','AMBIGUOUS','CLARIFICATION_REQUIRED','CANCELLED','INTERRUPTED','TIMEOUT'))
);
CREATE UNIQUE INDEX chat_one_running_turn ON chat_turn(organization_id,conversation_id) WHERE status='RUNNING';
CREATE TABLE chat_message (
 organization_id uuid NOT NULL, conversation_id uuid NOT NULL, owner_id uuid NOT NULL, id uuid NOT NULL,
 turn_id uuid NOT NULL, sequence integer NOT NULL, role varchar(16) NOT NULL CHECK(role IN('USER','ASSISTANT')),
 content text NOT NULL CHECK(octet_length(content)<=16384), created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(organization_id,id), UNIQUE(organization_id,conversation_id,sequence),
 FOREIGN KEY(organization_id,conversation_id,owner_id) REFERENCES chat_conversation(organization_id,id,owner_id),
 FOREIGN KEY(organization_id,conversation_id,turn_id) REFERENCES chat_turn(organization_id,conversation_id,id)
);
DO $$ BEGIN
 IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname='ecosync_runtime') THEN
  GRANT SELECT,INSERT,UPDATE ON chat_conversation,chat_turn TO ecosync_runtime;
  GRANT SELECT,INSERT ON chat_message TO ecosync_runtime;
 END IF;
END $$;
