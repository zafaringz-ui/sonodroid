/// Errors crossing the FFI boundary.
///
/// `flat_error` keeps the Kotlin surface simple: one exception type carrying the
/// rendered message. Structured details (HTTP status, retry hints) are added when
/// the provider clients land in Phase 3/4.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CoreError {
    #[error("not implemented yet: {0}")]
    NotImplemented(String),
    #[error("not signed in to {0}")]
    NotSignedIn(String),
    #[error("invalid argument: {0}")]
    InvalidArgument(String),
    #[error("network unavailable")]
    Offline,
    #[error("storage error: {0}")]
    Storage(String),
    #[error("provider rejected the request: {0}")]
    Provider(String),
    #[error("internal error: {0}")]
    Internal(String),
}

pub type CoreResult<T> = Result<T, CoreError>;
