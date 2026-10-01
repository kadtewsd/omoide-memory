-- Create table for storing album contents (photos)
CREATE TABLE omoide_memory.album_content (
    id                     UUID NOT NULL,
    album_id               UUID NOT NULL,
    photo_id               UUID NOT NULL,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by             VARCHAR(255),
    CONSTRAINT pk_album_content PRIMARY KEY (id),
    CONSTRAINT fk_album_content_album FOREIGN KEY (album_id)
        REFERENCES omoide_memory.album (id) ON DELETE CASCADE,
    CONSTRAINT fk_album_content_photo FOREIGN KEY (photo_id)
        REFERENCES omoide_memory.synced_omoide_photo (id)
);

COMMENT ON TABLE  omoide_memory.album_content IS 'アルバムコンテンツ紐付け';
COMMENT ON COLUMN omoide_memory.album_content.id IS 'サロゲートキー';
COMMENT ON COLUMN omoide_memory.album_content.album_id IS 'アルバムID';
COMMENT ON COLUMN omoide_memory.album_content.photo_id IS '写真ID';
COMMENT ON COLUMN omoide_memory.album_content.created_at IS 'レコード作成日時';
COMMENT ON COLUMN omoide_memory.album_content.created_by IS 'レコード作成者';
