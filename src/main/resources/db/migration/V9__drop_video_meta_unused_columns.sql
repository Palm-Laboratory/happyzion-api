ALTER TABLE youtube_video_meta
    DROP COLUMN IF EXISTS message_body,
    DROP COLUMN IF EXISTS thumbnail_override_url;
