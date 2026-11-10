ALTER TABLE accounts ADD COLUMN is_admin BOOLEAN NOT NULL DEFAULT FALSE;
CREATE TABLE themes (
 id UUID PRIMARY KEY,
 title VARCHAR(80) NOT NULL,
 description VARCHAR(1000) NOT NULL,
 activated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_by UUID NOT NULL REFERENCES accounts(id)
);
CREATE TABLE active_theme (
 singleton_id INTEGER PRIMARY KEY CHECK(singleton_id=1),
 theme_id UUID REFERENCES themes(id)
);
INSERT INTO active_theme(singleton_id,theme_id) VALUES(1,NULL);
CREATE TABLE general_likes (
 house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
 voter_id UUID NOT NULL REFERENCES accounts(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(house_id,voter_id)
);
CREATE TABLE theme_likes (
 theme_id UUID NOT NULL REFERENCES themes(id),
 house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
 voter_id UUID NOT NULL REFERENCES accounts(id),
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(theme_id,house_id,voter_id)
);
CREATE TABLE guestbook (
 id UUID PRIMARY KEY,
 house_id UUID NOT NULL REFERENCES houses(id) ON DELETE CASCADE,
 author_id UUID NOT NULL REFERENCES accounts(id),
 body VARCHAR(200) NOT NULL,
 created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_guestbook_house_created ON guestbook(house_id,created_at,id);
