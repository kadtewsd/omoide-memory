-- Add indexes for feed keyset pagination and comment count lookup
CREATE INDEX IF NOT EXISTS idx_synced_photo_capture_time_id
    ON omoide_memory.synced_omoide_photo (capture_time DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_synced_video_capture_time_id
    ON omoide_memory.synced_omoide_video (capture_time DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_comment_omoide_file_name
    ON omoide_memory.comment_omoide (file_name);
