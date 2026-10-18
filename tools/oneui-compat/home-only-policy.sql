-- Retire legacy rules that blocked user-selected native AppItems.
DROP TRIGGER IF EXISTS nemotron_no_target_shortcut;
DROP TRIGGER IF EXISTS nemotron_one_page;
DROP TRIGGER IF EXISTS nemotron_no_native_insert;
DROP TRIGGER IF EXISTS nemotron_no_native_readd;
DROP TRIGGER IF EXISTS nemotron_no_direct_app_shortcut;
DROP TRIGGER IF EXISTS nemotron_no_social_icons;
-- One statement per line; applied to the active One UI workspace DB before Room loads items.
CREATE TABLE IF NOT EXISTS nemotron_native_allowlist (id INTEGER PRIMARY KEY, component TEXT);
INSERT OR IGNORE INTO nemotron_native_allowlist SELECT id,component FROM item WHERE type='app' AND NOT (component LIKE 'com.instagram.android/%' OR component LIKE 'com.whatsapp/%' OR component LIKE 'com.sec.android.app.shealth/%') AND NOT EXISTS (SELECT 1 FROM nemotron_native_allowlist WHERE id=-2147483648);
INSERT OR IGNORE INTO nemotron_native_allowlist VALUES (-2147483648,'initialized');
-- Do not delete pinned shortcuts here. WorkspaceViewModel blocks automatic
-- AppItem additions while preserving shortcuts the user explicitly pins.
