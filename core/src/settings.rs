//! Default settings and known search engines.

pub const DEFAULT_SEARCH_ENGINE: &str = "https://duckduckgo.com/?q=%s";

/// Default start page — the maintainer's GitHub page.
pub const DEFAULT_HOME_URL: &str = "https://github.com/kd1wso";

/// A search engine the user can pick in Settings.
#[derive(Debug, Clone, uniffi::Record)]
pub struct SearchEngine {
    pub id: String,
    pub name: String,
    /// URL template containing `%s` for the query.
    pub url_template: String,
}

/// Built-in search engines offered in the Settings picker.
pub fn builtin_search_engines() -> Vec<SearchEngine> {
    vec![
        SearchEngine {
            id: "duckduckgo".into(),
            name: "DuckDuckGo".into(),
            url_template: "https://duckduckgo.com/?q=%s".into(),
        },
        SearchEngine {
            id: "google".into(),
            name: "Google".into(),
            url_template: "https://www.google.com/search?q=%s".into(),
        },
        SearchEngine {
            id: "bing".into(),
            name: "Bing".into(),
            url_template: "https://www.bing.com/search?q=%s".into(),
        },
        SearchEngine {
            id: "yandex".into(),
            name: "Yandex".into(),
            url_template: "https://yandex.ru/search/?text=%s".into(),
        },
        SearchEngine {
            id: "brave".into(),
            name: "Brave".into(),
            url_template: "https://search.brave.com/search?q=%s".into(),
        },
    ]
}