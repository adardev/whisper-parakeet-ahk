-- Retire legacy rules that blocked user-selected native AppItems.
DROP TRIGGER IF EXISTS nemotron_no_target_shortcut;
DROP TRIGGER IF EXISTS nemotron_one_page;
DROP TRIGGER IF EXISTS nemotron_no_native_insert;
DROP TRIGGER IF EXISTS nemotron_no_native_readd;
DROP TRIGGER IF EXISTS nemotron_no_direct_app_shortcut;
-- One statement per line; applied only to HomeOnly.db before Room loads items.
CREATE TABLE IF NOT EXISTS nemotron_native_allowlist (id INTEGER PRIMARY KEY, component TEXT);
INSERT OR IGNORE INTO nemotron_native_allowlist SELECT id,component FROM item WHERE type='app' AND NOT (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%') AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist WHERE id=-2147483648);
INSERT OR IGNORE INTO nemotron_native_allowlist VALUES (-2147483648,'initialized');
CREATE TRIGGER IF NOT EXISTS nemotron_no_direct_app_shortcut BEFORE INSERT ON item WHEN NEW.type='deepshortcut' AND instr(NEW.intent,'S.shortcut_id=nemotron_app_')>0 BEGIN SELECT RAISE(IGNORE); END;
DELETE FROM item WHERE type='deepshortcut' AND instr(intent,'S.shortcut_id=nemotron_app_')>0;
