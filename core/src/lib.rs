//! Hori Core — engine-agnostic browser logic.
//!
//! The Android layer (Kotlin) talks to this crate through UniFFI-generated
//! bindings. GeckoView sessions stay on the Kotlin side; nothing in here
//! knows about Android objects.

uniffi::setup_scaffolding!();

mod navigation;
mod settings;
mod storage;

pub use navigation::{classify_input, search_url};
pub use settings::SearchEngine;

use std::path::Path;
use std::sync::{Arc, Mutex};

#[derive(Debug, thiserror::Error, uniffi::Error)]
pub enum HoriError {
    #[error("storage error: {reason}")]
    Storage { reason: String },
}

impl From<rusqlite::Error> for HoriError {
    fn from(e: rusqlite::Error) -> Self {
        HoriError::Storage {
            reason: e.to_string(),
        }
    }
}

/// What the address bar input means. The Kotlin layer uses this to decide
/// whether to load a URL directly or run a search.
#[derive(Debug, Clone, uniffi::Enum)]
pub enum InputKind {
    Url { url: String },
    Search { url: String, query: String },
}

/// A persisted browser setting. Values are strings; typed accessors wrap them.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Setting {
    pub key: String,
    pub value: String,
}

/// One visited site. `visited_at` is Unix epoch milliseconds.
#[derive(Debug, Clone, uniffi::Record)]
pub struct HistoryEntry {
    pub url: String,
    pub title: String,
    pub visited_at: i64,
}

/// A saved bookmark. `id` is its storage primary key; `created_at` is Unix
/// epoch milliseconds; `position` orders the bookmark list.
#[derive(Debug, Clone, uniffi::Record)]
pub struct Bookmark {
    pub id: i64,
    pub url: String,
    pub title: String,
    pub created_at: i64,
    pub position: i64,
}

/// Entry point of Hori Core. Owns the SQLite database under `data_dir`.
#[derive(uniffi::Object)]
pub struct BrowserCore {
    db: Mutex<rusqlite::Connection>,
}

#[uniffi::export]
impl BrowserCore {
    /// Opens (or creates) `hori.db` inside `data_dir`.
    #[uniffi::constructor]
    pub fn new(data_dir: String) -> Result<Arc<Self>, HoriError> {
        let dir = Path::new(&data_dir);
        std::fs::create_dir_all(dir).map_err(|e| HoriError::Storage {
            reason: format!("cannot create data dir: {e}"),
        })?;
        let conn = rusqlite::Connection::open(dir.join("hori.db"))?;
        storage::migrate(&conn)?;
        Ok(Arc::new(Self {
            db: Mutex::new(conn),
        }))
    }

    /// Hori core version (shown in the About screen).
    pub fn version(&self) -> String {
        env!("CARGO_PKG_VERSION").to_string()
    }

    /// Interprets address-bar input: a URL to load, or a search to run.
    pub fn classify_input(&self, input: String) -> Result<InputKind, HoriError> {
        let engine = self.search_engine()?;
        Ok(navigation::classify(&input, &engine))
    }

    /// Builds a search URL for `query` using the configured engine.
    pub fn search_url(&self, query: String) -> Result<String, HoriError> {
        let engine = self.search_engine()?;
        Ok(navigation::search_url(&engine, &query))
    }

    pub fn get_setting(&self, key: String) -> Result<Option<String>, HoriError> {
        let db = self.db.lock().unwrap();
        storage::get_setting(&db, &key).map_err(Into::into)
    }

    pub fn set_setting(&self, key: String, value: String) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::set_setting(&db, &key, &value).map_err(Into::into)
    }

    pub fn all_settings(&self) -> Result<Vec<Setting>, HoriError> {
        let db = self.db.lock().unwrap();
        storage::all_settings(&db).map_err(Into::into)
    }

    /// The search engine template, e.g. "https://duckduckgo.com/?q=%s".
    pub fn search_engine(&self) -> Result<String, HoriError> {
        Ok(self
            .get_setting("search_engine".into())?
            .unwrap_or_else(|| settings::DEFAULT_SEARCH_ENGINE.to_string()))
    }

    pub fn home_url(&self) -> Result<String, HoriError> {
        Ok(self
            .get_setting("home_url".into())?
            .unwrap_or_else(|| settings::DEFAULT_HOME_URL.to_string()))
    }

    /// Theme mode: "system", "light" or "dark". Defaults to "system".
    pub fn theme_mode(&self) -> Result<String, HoriError> {
        Ok(self
            .get_setting("theme_mode".into())?
            .unwrap_or_else(|| "system".to_string()))
    }

    /// The built-in search engines available in the Settings picker.
    pub fn search_engines(&self) -> Vec<SearchEngine> {
        settings::builtin_search_engines()
    }

    // --- History ---------------------------------------------------------

    pub fn record_visit(&self, url: String, title: String) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::record_visit(&db, &url, &title, now_millis()).map_err(Into::into)
    }

    pub fn recent_history(&self, limit: u32) -> Result<Vec<HistoryEntry>, HoriError> {
        let db = self.db.lock().unwrap();
        storage::recent_history(&db, limit).map_err(Into::into)
    }

    pub fn history_count(&self) -> Result<u32, HoriError> {
        let db = self.db.lock().unwrap();
        storage::history_count(&db).map_err(Into::into)
    }

    pub fn clear_history(&self) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::clear_history(&db).map_err(Into::into)
    }

    /// Wipes all persisted user data in one call (history + non-sensitive
    /// settings are kept; this exists for the "clear data" privacy action).
    pub fn clear_browsing_data(&self) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::clear_history(&db)?;
        storage::set_setting(&db, "history_cleared_at", &now_millis().to_string())?;
        Ok(())
    }

    // --- Bookmarks -------------------------------------------------------

    /// Adds (or re-titles) a bookmark and returns its row id.
    pub fn add_bookmark(&self, url: String, title: String) -> Result<i64, HoriError> {
        let db = self.db.lock().unwrap();
        storage::add_bookmark(&db, &url, &title, now_millis()).map_err(Into::into)
    }

    pub fn remove_bookmark(&self, id: i64) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::remove_bookmark(&db, id).map_err(Into::into)
    }

    pub fn remove_bookmark_by_url(&self, url: String) -> Result<(), HoriError> {
        let db = self.db.lock().unwrap();
        storage::remove_bookmark_by_url(&db, &url).map_err(Into::into)
    }

    pub fn is_bookmarked(&self, url: String) -> Result<bool, HoriError> {
        let db = self.db.lock().unwrap();
        storage::is_bookmarked(&db, &url).map_err(Into::into)
    }

    /// All bookmarks in display order.
    pub fn bookmarks(&self) -> Result<Vec<Bookmark>, HoriError> {
        let db = self.db.lock().unwrap();
        storage::all_bookmarks(&db).map_err(Into::into)
    }

    pub fn bookmark_count(&self) -> Result<u32, HoriError> {
        let db = self.db.lock().unwrap();
        storage::bookmark_count(&db).map_err(Into::into)
    }
}

/// Current Unix time in milliseconds.
fn now_millis() -> i64 {
    use std::time::{SystemTime, UNIX_EPOCH};
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as i64)
        .unwrap_or(0)
}