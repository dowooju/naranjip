CREATE TABLE houses (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL UNIQUE REFERENCES accounts(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0 CHECK (revision >= 0),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE placed_furniture (
    id UUID PRIMARY KEY,
    house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
    type_id VARCHAR(40) NOT NULL,
    x INTEGER NOT NULL CHECK (x BETWEEN 0 AND 7),
    y INTEGER NOT NULL CHECK (y BETWEEN 0 AND 7),
    rotation BOOLEAN NOT NULL DEFAULT FALSE,
    wall VARCHAR(5) CHECK (wall IN ('left', 'right')),
    parent_id UUID,
    slot INTEGER NOT NULL DEFAULT -1,
    sort_order INTEGER NOT NULL,
    UNIQUE (house_id, id),
    UNIQUE (house_id, parent_id, slot),
    FOREIGN KEY (house_id, parent_id) REFERENCES placed_furniture(house_id, id) ON DELETE CASCADE,
    CHECK ((parent_id IS NULL AND slot = -1) OR
           (parent_id IS NOT NULL AND slot >= 0 AND NOT rotation AND wall IS NULL AND x = 0 AND y = 0))
);
CREATE INDEX idx_placed_furniture_house_order ON placed_furniture(house_id, sort_order);
INSERT INTO houses(id, owner_id) SELECT id, id FROM accounts;
