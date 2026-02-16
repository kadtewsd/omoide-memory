-- Create table for storing synced file metadata

CREATE TABLE synced_omoide_photo (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY NOT NULL,
    file_name           VARCHAR(255)    NOT NULL,
    server_path         TEXT            NOT NULL,
    capture_time        TIMESTAMP,
    latitude            DECIMAL(10, 7),
    longitude           DECIMAL(10, 7),
    altitude            DECIMAL(10, 2),
    location_name       TEXT,
    device_make         VARCHAR(255),
    device_model        VARCHAR(255),
    aperture            DECIMAL(5, 2),
    shutter_speed       VARCHAR(50),
    iso_speed           INT,
    focal_length        DECIMAL(6, 2),
    focal_length_35mm   INT,
    white_balance       VARCHAR(50),
    image_width         INT,
    image_height        INT,
    orientation         SMALLINT,
    file_size_bytes     BIGINT,
    created_at          TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    CONSTRAINT pk_synced_omoide_photo PRIMARY KEY (id),
    CONSTRAINT uq_synced_omoide_photo_file_name UNIQUE (file_name)
);

COMMENT ON TABLE synced_omoide_photo IS '同期済みおもいで写真';
COMMENT ON COLUMN synced_omoide_photo.id IS 'サロゲートキー';
COMMENT ON COLUMN synced_omoide_photo.file_name IS 'ファイル名（ユニーク）';
COMMENT ON COLUMN synced_omoide_photo.server_path IS 'サーバー上のパス';
COMMENT ON COLUMN synced_omoide_photo.capture_time IS '撮影日時';
COMMENT ON COLUMN synced_omoide_photo.latitude IS '緯度';
COMMENT ON COLUMN synced_omoide_photo.longitude IS '経度';
COMMENT ON COLUMN synced_omoide_photo.altitude IS '高度（メートル）';
COMMENT ON COLUMN synced_omoide_photo.location_name IS '逆ジオコーディングによる地名';
COMMENT ON COLUMN synced_omoide_photo.device_make IS 'デバイスメーカー';
COMMENT ON COLUMN synced_omoide_photo.device_model IS 'デバイスモデル';
COMMENT ON COLUMN synced_omoide_photo.aperture IS '絞り値（F値）';
COMMENT ON COLUMN synced_omoide_photo.shutter_speed IS 'シャッタースピード';
COMMENT ON COLUMN synced_omoide_photo.iso_speed IS 'ISO感度';
COMMENT ON COLUMN synced_omoide_photo.focal_length IS '焦点距離（mm）';
COMMENT ON COLUMN synced_omoide_photo.focal_length_35mm IS '35mm換算焦点距離';
COMMENT ON COLUMN synced_omoide_photo.white_balance IS 'ホワイトバランス';
COMMENT ON COLUMN synced_omoide_photo.image_width IS '画像幅（px）';
COMMENT ON COLUMN synced_omoide_photo.image_height IS '画像高さ（px）';
COMMENT ON COLUMN synced_omoide_photo.orientation IS 'EXIF回転情報（1〜8）';
COMMENT ON COLUMN synced_omoide_photo.file_size_bytes IS 'ファイルサイズ（バイト）';
COMMENT ON COLUMN synced_omoide_photo.created_at IS 'レコード作成日時';
COMMENT ON COLUMN synced_omoide_photo.created_by IS 'レコード作成者';
