-- One statement per line; applied only to HomeOnly.db before Room loads items.
CREATE TABLE IF NOT EXISTS nemotron_native_allowlist (id INTEGER PRIMARY KEY, component TEXT);
INSERT OR IGNORE INTO nemotron_native_allowlist SELECT id,component FROM item WHERE type='app' AND NOT (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%') AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist WHERE id=-2147483648);
INSERT OR IGNORE INTO nemotron_native_allowlist VALUES (-2147483648,'initialized');
CREATE TRIGGER IF NOT EXISTS nemotron_no_native_insert BEFORE INSERT ON item WHEN NEW.type='app' AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist a WHERE a.id=NEW.id AND a.component=NEW.component) BEGIN SELECT RAISE(IGNORE); END;
CREATE TRIGGER IF NOT EXISTS nemotron_no_native_readd BEFORE UPDATE OF container_id ON item WHEN NEW.type='app' AND OLD.container_id=-1 AND NEW.container_id>=0 BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE type='app' AND (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%');
-- Latest request: no Instagram/WhatsApp icons anywhere in Home, including pinned shortcuts.
CREATE TRIGGER IF NOT EXISTS nemotron_no_target_shortcut BEFORE INSERT ON item WHEN NEW.type IN ('shortcut','deepshortcut') AND (NEW.intent LIKE '%shortcut_com.instagram.android%' OR NEW.intent LIKE '%shortcut_com.whatsapp%' OR NEW.intent LIKE '%S.pkg=com.instagram.android%' OR NEW.intent LIKE '%S.pkg=com.whatsapp%') BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE type IN ('shortcut','deepshortcut') AND (intent LIKE '%shortcut_com.instagram.android%' OR intent LIKE '%shortcut_com.whatsapp%' OR intent LIKE '%S.pkg=com.instagram.android%' OR intent LIKE '%S.pkg=com.whatsapp%');
DELETE FROM item_group WHERE type='page' AND id!=(SELECT id FROM item_group WHERE type='page' ORDER BY rank,id LIMIT 1) AND NOT EXISTS (SELECT 1 FROM item WHERE container_type=0 AND container_id=item_group.id);
CREATE TRIGGER IF NOT EXISTS nemotron_one_page BEFORE INSERT ON item_group WHEN NEW.type='page' AND EXISTS (SELECT 1 FROM item_group WHERE type='page' AND container_id=NEW.container_id) BEGIN SELECT RAISE(IGNORE); END;
