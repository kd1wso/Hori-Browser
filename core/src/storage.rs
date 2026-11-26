//! SQLite persistence: settings, history and bookmarks.

use crate::{Bookmark, HoriError, HistoryEntry, Setting};
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

        CREATE TABLE IF NOT EXISTS bookmarks (
            id         INTEGER PRIMARY KEY AUTOINCREMENT,
            url        TEXT NOT NULL UNIQUE,
            title      TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            position   INTEGER NOT NULL DEFAULT 0
        );
        CREATE INDEX IF NOT EXISTS idx_bookmarks_position
            ON bookmarks (position ASC, created_at ASC);

        PRAGMA user_version = 3;",
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

// --- History ---------------------------------------------------------------

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

// --- Bookmarks -------------------------------------------------------------

/// A single bookmark row with its storage id.
pub fn all_bookmarks(conn: &Connection) -> rusqlite::Result<Vec<Bookmark>> {
    let mut stmt = conn.prepare(
        "SELECT id, url, title, created_at, position
         FROM bookmarks
         ORDER BY position ASC, created_at ASC",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(Bookmark {
            id: row.get(0)?,
            url: row.get(1)?,
            title: row.get(2)?,
            created_at: row.get(3)?,
            position: row.get(4)?,
        })
    })?;
    rows.collect()
}

/// Whether [url] is already bookmarked.
pub fn is_bookmarked(conn: &Connection, url: &str) -> rusqlite::Result<bool> {
    let count: u32 = conn.query_row(
        "SELECT COUNT(*) FROM bookmarks WHERE url = ?1",
        params![url],
        |row| row.get(0),
    )?;
    Ok(count > 0)
}

/// Adds a bookmark. Re-adding an existing URL updates its title.
/// Returns the row id.
pub fn add_bookmark(
    conn: &Connection,
    url: &str,
    title: &str,
    created_at: i64,
) -> rusqlite::Result<i64> {
    // Position = next after the current max, so new bookmarks go last.
    let next_pos: i64 = conn.query_row(
        "SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks",
        [],
        |row| row.get(0),
    )?;
    conn.execute(
        "INSERT INTO bookmarks (url, title, created_at, position)
         VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(url) DO UPDATE SET
             title = CASE WHEN excluded.title <> '' THEN excluded.title
                          ELSE bookmarks.title END",
        params![url, title, created_at, next_pos],
    )?;
    conn.query_row(
        "SELECT id FROM bookmarks WHERE url = ?1",
        params![url],
        |row| row.get(0),
    )
}

pub fn remove_bookmark(conn: &Connection, id: i64) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM bookmarks WHERE id = ?1", params![id])?;
    Ok(())
}

pub fn remove_bookmark_by_url(conn: &Connection, url: &str) -> rusqlite::Result<()> {
    conn.execute("DELETE FROM bookmarks WHERE url = ?1", params![url])?;
    Ok(())
}

pub fn bookmark_count(conn: &Connection) -> rusqlite::Result<u32> {
    conn.query_row("SELECT COUNT(*) FROM bookmarks", [], |row| row.get(0))
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

    #[test]
    fn bookmarks_crud() {
        let conn = Connection::open_in_memory().unwrap();
        migrate(&conn).unwrap();

        assert!(!is_bookmarked(&conn, "https://a.example").unwrap());
        let id = add_bookmark(&conn, "https://a.example", "A", 100).unwrap();
        assert!(is_bookmarked(&conn, "https://a.example").unwrap());

        // Re-adding updates the title and keeps a single row.
        add_bookmark(&conn, "https://a.example", "A renamed", 200).unwrap();
        assert_eq!(bookmark_count(&conn).unwrap(), 1);

        let all = all_bookmarks(&conn).unwrap();
        assert_eq!(all.len(), 1);
        assert_eq!(all[0].title, "A renamed");
        assert_eq!(all[0].id, id);

        // Ordering: positions ascend.
        add_bookmark(&conn, "https://b.example", "B", 300).unwrap();
        let all = all_bookmarks(&conn).unwrap();
        assert_eq!(all[0].url, "https://a.example");
        assert_eq!(all[1].url, "https://b.example");

        remove_bookmark(&conn, id).unwrap();
        assert_eq!(bookmark_count(&conn).unwrap(), 1);
        remove_bookmark_by_url(&conn, "https://b.example").unwrap();
        assert_eq!(bookmark_count(&conn).unwrap(), 0);
    }
}