-- Manual pinned shortcuts and user-created pages are allowed.
DROP TRIGGER IF EXISTS nemotron_no_target_shortcut;
DROP TRIGGER IF EXISTS nemotron_one_page;
-- One statement per line; applied only to HomeOnly.db before Room loads items.
CREATE TABLE IF NOT EXISTS nemotron_native_allowlist (id INTEGER PRIMARY KEY, component TEXT);
INSERT OR IGNORE INTO nemotron_native_allowlist SELECT id,component FROM item WHERE type='app' AND NOT (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%') AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist WHERE id=-2147483648);
INSERT OR IGNORE INTO nemotron_native_allowlist VALUES (-2147483648,'initialized');
CREATE TRIGGER IF NOT EXISTS nemotron_no_native_insert BEFORE INSERT ON item WHEN NEW.type='app' AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist a WHERE a.id=NEW.id AND a.component=NEW.component) BEGIN SELECT RAISE(IGNORE); END;
CREATE TRIGGER IF NOT EXISTS nemotron_no_native_readd BEFORE UPDATE OF container_id ON item WHEN NEW.type='app' AND OLD.container_id=-1 AND NEW.container_id>=0 BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE type='app' AND (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%');
