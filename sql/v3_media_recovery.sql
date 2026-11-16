CREATE TABLE IF NOT EXISTS ops_media_state (
    path_hash binary(32) NOT NULL,
    relative_path varbinary(2048) NOT NULL,
    deleted tinyint NOT NULL DEFAULT 0,
    byte_length bigint NOT NULL DEFAULT 0,
    PRIMARY KEY(path_hash)
) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS ops_media_chunk (
    path_hash binary(32) NOT NULL,
    part int NOT NULL,
    content mediumblob NOT NULL,
    PRIMARY KEY(path_hash,part)
) ENGINE=InnoDB;
