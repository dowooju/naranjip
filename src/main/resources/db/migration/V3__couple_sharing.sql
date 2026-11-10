CREATE TABLE house_members (
 account_id UUID PRIMARY KEY REFERENCES accounts(id),
 house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
 joined_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen_revision BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_house_members_house ON house_members(house_id);
INSERT INTO house_members(account_id,house_id) SELECT owner_id,id FROM houses;
ALTER TABLE houses DROP COLUMN owner_id;
CREATE TABLE invites (
 id UUID PRIMARY KEY,
 code VARCHAR(7) NOT NULL UNIQUE,
 house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
 issued_by UUID NOT NULL REFERENCES accounts(id),
 expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
 consumed_at TIMESTAMP WITH TIME ZONE,
 revoked_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_invites_house ON invites(house_id);
