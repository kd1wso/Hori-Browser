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

    /// Interprets address-bar input: a URL to load, or a search to run.
    pub fn classify_input(&self, input: String) -> Result<InputKind, HoriError> {
        let engine = self.search_engine()?;
        Ok(navigation::classify(&input, &engine))
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
}