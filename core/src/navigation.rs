//! Address-bar input classification: URL vs search query.

use crate::InputKind;
use url::Url;

/// Builds a search URL from an engine template containing `%s`.
pub fn search_url(template: &str, query: &str) -> String {
    let encoded: String = url::form_urlencoded::byte_serialize(query.as_bytes()).collect();
    if template.contains("%s") {
        template.replace("%s", &encoded)
    } else {
        format!("{template}{encoded}")
    }
}

/// Kotlin-facing wrapper (also usable from Rust tests).
pub fn classify_input(input: &str, engine: &str) -> InputKind {
    classify(input, engine)
}

pub fn classify(input: &str, engine: &str) -> InputKind {
    let trimmed = input.trim();
    if trimmed.is_empty() {
        return InputKind::Search {
            url: search_url(engine, ""),
            query: String::new(),
        };
    }

    // Explicit scheme: trust it, but only for schemes a browser can load.
    if let Ok(parsed) = Url::parse(trimmed) {
        match parsed.scheme() {
            "http" | "https" | "about" | "data" | "file" => {
                return InputKind::Url {
                    url: trimmed.to_string(),
                };
            }
            _ => {}
        }
    }

    // No whitespace and looks like a host (has a dot, or localhost, or an IP)
    // -> treat as a URL and default to https.
    let looks_like_host = !trimmed.contains(char::is_whitespace)
        && (trimmed.contains('.') || trimmed.starts_with("localhost"))
        && !trimmed.ends_with('.');

    if looks_like_host {
        let candidate = format!("https://{trimmed}");
        if let Ok(parsed) = Url::parse(&candidate) {
            if parsed.host_str().is_some() {
                return InputKind::Url { url: candidate };
            }
        }
    }

    InputKind::Search {
        url: search_url(engine, trimmed),
        query: trimmed.to_string(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const ENGINE: &str = "https://duckduckgo.com/?q=%s";

    #[test]
    fn explicit_https() {
        match classify("https://example.com/a?b=1", ENGINE) {
            InputKind::Url { url } => assert_eq!(url, "https://example.com/a?b=1"),
            _ => panic!("expected url"),
        }
    }

    #[test]
    fn bare_host() {
        match classify("github.com", ENGINE) {
            InputKind::Url { url } => assert_eq!(url, "https://github.com"),
            _ => panic!("expected url"),
        }
    }

    #[test]
    fn phrase_is_search() {
        match classify("rust geckoview tutorial", ENGINE) {
            InputKind::Search { url, query } => {
                assert_eq!(query, "rust geckoview tutorial");
                assert!(url.starts_with("https://duckduckgo.com/?q=rust+geckoview"));
            }
            _ => panic!("expected search"),
        }
    }

    #[test]
    fn cyrillic_query() {
        match classify("погода москва", ENGINE) {
            InputKind::Search { url, .. } => {
                assert!(url.contains("%D0%BF"));
            }
            _ => panic!("expected search"),
        }
    }

    #[test]
    fn localhost() {
        match classify("localhost:8080", ENGINE) {
            InputKind::Url { url } => assert_eq!(url, "https://localhost:8080"),
            _ => panic!("expected url"),
        }
    }

    #[test]
    fn single_word_is_search() {
        assert!(matches!(classify("wikipedia", ENGINE), InputKind::Search { .. }));
    }
}