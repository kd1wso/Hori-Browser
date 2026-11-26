//! SQLite persistence: settings + browsing history (recent sites).

use crate::{HoriError, HistoryEntry, Setting};
use rusqlite::{params, Connection, OptionalExtension};

pub fn migrate(conn: &Connection) -> Result<(), HoriError> {
    conn.pragma_update(None, "journal_mode", "WAL")?;
    conn.execute_batch(
        "CREATE TABLE IF NOT EXISTS settings (
            key   TEXT PRIMARY KEY NOT NULL,
            value TEXT NOT NULL
        );

        CREATE TABLE IF NOT EXISTS history (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            url        TEXT NOT NULL,
            title      TEXT NOT NULL DEFAULT '',
            visited_at INTEGER NOT NULL
        );
        CREATE INDEX IF NOT EXISTS idx_history_visited
            ON history (visited_at DESC);
        CREATE UNIQUE INDEX IF NOT EXISTS idx_history_url
            ON history (url);

        PRAGMA user_version = 2;",
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

/// Records a visit; repeated visits to the same URL refresh timestamp and title.
pub fn record_visit(
    conn: &Connection,
    url: &str,
    title: &str,
    visited_at: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO history (url, title, visited_at) VALUES (?1, ?2, ?3)
         ON CONFLICT(url) DO UPDATE SET
             visited_at = excluded.visited_at,
             title = CASE WHEN excluded.title <> '' THEN excluded.title ELSE history.title END",
        params![url, title, visited_at],
    )?;
    Ok(())
}

/// Most recently visited distinct sites, newest first.
pub fn recent_history(conn: &Connection, limit: u32) -> rusqlite::Result<Vec<HistoryEntry>> {
    let mut stmt = conn.prepare(
        "SELECT url, title, visited_at
         FROM history
         ORDER BY visited_at DESC
         LIMIT ?1",
    )?;
    let rows = stmt.query_map(params![limit], |row| {
        Ok(HistoryEntry {
            url: row.get(0)?,
            title: row.get(1)?,
            visited_at: row.get(2)?,
        })
    })?;
    rows.collect()
}

pub fn history_count(conn: &Connection) -> rusqlite::Result<u32> {
    conn.query_row("SELECT COUNT(*) FROM history", [], |row| row.get(0))
}

pub fn clear_history(conn: &Connection) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM history", [])?;
    Ok(())
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

    #[test]
    fn history_dedups_and_orders() {
        let conn = Connection::open_in_memory().unwrap();
        migrate(&conn).unwrap();
        record_visit(&conn, "https://a.example", "A", 100).unwrap();
        record_visit(&conn, "https://b.example", "B", 200).unwrap();
        record_visit(&conn, "https://a.example", "A2", 300).unwrap();

        let recent = recent_history(&conn, 10).unwrap();
        assert_eq!(recent.len(), 2);
        assert_eq!(recent[0].url, "https://a.example");
        assert_eq!(recent[0].title, "A2");
        assert_eq!(history_count(&conn).unwrap(), 2);

        clear_history(&conn).unwrap();
        assert_eq!(history_count(&conn).unwrap(), 0);
    }
}