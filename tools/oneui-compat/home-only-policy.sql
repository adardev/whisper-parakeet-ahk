-- Retire legacy rules that blocked user-selected native AppItems.
DROP TRIGGER IF EXISTS nemotron_no_target_shortcut;
DROP TRIGGER IF EXISTS nemotron_one_page;
DROP TRIGGER IF EXISTS nemotron_no_native_insert;
DROP TRIGGER IF EXISTS nemotron_no_native_readd;
DROP TRIGGER IF EXISTS nemotron_no_direct_app_shortcut;
-- One statement per line; applied to the active One UI workspace DB before Room loads items.
CREATE TABLE IF NOT EXISTS nemotron_native_allowlist (id INTEGER PRIMARY KEY, component TEXT);
INSERT OR IGNORE INTO nemotron_native_allowlist SELECT id,component FROM item WHERE type='app' AND NOT (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%') AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist WHERE id=-2147483648);
INSERT OR IGNORE INTO nemotron_native_allowlist VALUES (-2147483648,'initialized');
CREATE TRIGGER IF NOT EXISTS nemotron_no_direct_app_shortcut BEFORE INSERT ON item WHEN NEW.type='deepshortcut' AND instr(NEW.intent,'S.shortcut_id=nemotron_app_')>0 BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE type='deepshortcut' AND instr(intent,'S.shortcut_id=nemotron_app_')>0;
-- These two apps stay off Home even when a saved shortcut or dock item exists.
CREATE TRIGGER IF NOT EXISTS nemotron_no_social_icons BEFORE INSERT ON item WHEN instr(COALESCE(NEW.component,''),'com.whatsapp/')>0 OR instr(COALESCE(NEW.component,''),'com.instagram.android/')>0 OR instr(COALESCE(NEW.intent,''),'com.whatsapp')>0 OR instr(COALESCE(NEW.intent,''),'com.instagram.android')>0 BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE component LIKE 'com.whatsapp/%' OR component LIKE 'com.instagram.android/%' OR intent LIKE '%com.whatsapp%' OR intent LIKE '%com.instagram.android%';
