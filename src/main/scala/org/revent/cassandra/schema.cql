CREATE TABLE IF NOT EXISTS events (
  stream_id    TEXT,
  version      INT,
  event_type   TEXT,
  event_data   BLOB,
  event_time   BIGINT,
  max_version  INT STATIC,
  PRIMARY KEY (stream_id, version)
) WITH compression = {'sstable_compression': 'LZ4Compressor'};
