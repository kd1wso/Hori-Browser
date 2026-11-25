//! Library-mode bindgen: generates foreign bindings from the compiled crate.
//! Usage: cargo run --bin uniffi-bindgen -- generate --library <path-to-dll> --language kotlin --out-dir <dir>

fn main() {
    uniffi::uniffi_bindgen_main()
}