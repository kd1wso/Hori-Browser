//! Default settings for Hori 0.1. Users can override these from the UI;
//! the values are persisted in SQLite (see `storage`).

pub const DEFAULT_SEARCH_ENGINE: &str = "https://duckduckgo.com/?q=%s";

/// Default start page — the maintainer's GitHub page.
pub const DEFAULT_HOME_URL: &str = "https://github.com/kd1wso";