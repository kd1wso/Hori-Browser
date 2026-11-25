//! SQLite persistence. Schema migrations live here; future milestones add
//! bookmarks/history/tabs tables alongside `settings`.

use crate::{HoriError, Setting};
use rusqlite::{params, Connection, OptionalExtension};

pub fn migrate(conn: &Connection) -> Result<(), HoriError> {
    conn.pragma_update(None, "journal_mode", "WAL")?;
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS settings (
            key   TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        );
        PRAGMA user_version = 1;",
    )?;
    Ok(())
}

pub fn get_setting(conn: &Connection, key: &str) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT value FROM settings WHERE key = ?1",
        params![key],
        |row| row.get(0),
    )
    .optional()
}

pub fn set_setting(conn: &Connection, key: &str, value: &str) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO settings (key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![key, value],
    )?;
    Ok(())
}

pub fn all_settings(conn: &Connection) -> rusqlite::Result<Vec<Setting>> {
    let mut stmt = conn.prepare("SELECT key, value FROM settings ORDER BY key")?;
    let rows = stmt.query_map([], |row| {
        Ok(Setting {
            key: row.get(0)?,
            value: row.get(1)?,
        })
    })?;
    rows.collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip() {
        let conn = Connection::open_in_memory().unwrap();
        migrate(&conn).unwrap();
        assert_eq!(get_setting(&conn, "home_url").unwrap(), None);
        set_setting(&conn, "home_url", "https://example.org").unwrap();
        set_setting(&conn, "home_url", "https://example.com").unwrap();
        assert_eq!(
            get_setting(&conn, "home_url").unwrap().unwrap(),
            "https://example.com"
        );
        assert_eq!(all_settings(&conn).unwrap().len(), 1);
    }
}