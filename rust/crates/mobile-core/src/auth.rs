//! Account and session state.
//!
//! Secrets never live in this module. The ARL and the Google cookie jar stay in
//! Android Keystore-backed storage (see `platform-secure`); Rust only tracks which
//! providers are usable, which account is attached and what the plan allows.

use crate::models::Provider;
use crate::quality::PlanTier;

#[derive(Clone, Copy, Debug, PartialEq, Eq, uniffi::Enum)]
pub enum AccountStatus {
    SignedOut,
    /// WebView is open, no cookie captured yet.
    Connecting,
    SignedIn,
    /// Cookies expired or Google asked for a re-auth.
    NeedsAttention,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct AccountSummary {
    pub id: String,
    pub display_name: String,
    pub plan: PlanTier,
    /// FLAC 24 for Deezer Hi-Fi, AAC 256 for YouTube Premium.
    pub is_lossless_capable: bool,
}

#[derive(Clone, Debug, PartialEq, uniffi::Record)]
pub struct ProviderAuth {
    pub provider: Provider,
    pub status: AccountStatus,
    pub account: Option<AccountSummary>,
    /// Human readable, already redacted. Safe to show and to log.
    pub detail: Option<String>,
}

impl ProviderAuth {
    #[must_use]
    pub fn signed_out(provider: Provider) -> Self {
        Self {
            provider,
            status: AccountStatus::SignedOut,
            account: None,
            detail: None,
        }
    }
}

#[derive(Debug, Default)]
pub struct AuthStore {
    entries: Vec<ProviderAuth>,
}

impl AuthStore {
    #[must_use]
    pub fn new() -> Self {
        Self {
            entries: vec![
                ProviderAuth::signed_out(Provider::Deezer),
                ProviderAuth::signed_out(Provider::YouTubeMusic),
            ],
        }
    }

    #[must_use]
    pub fn snapshot(&self) -> Vec<ProviderAuth> {
        self.entries.clone()
    }

    #[must_use]
    pub fn get(&self, provider: Provider) -> ProviderAuth {
        self.entries
            .iter()
            .find(|entry| entry.provider == provider)
            .cloned()
            .unwrap_or_else(|| ProviderAuth::signed_out(provider))
    }

    pub fn set_connecting(&mut self, provider: Provider) {
        self.update(provider, AccountStatus::Connecting, None, None);
    }

    pub fn set_signed_in(&mut self, provider: Provider, account: AccountSummary) {
        self.update(provider, AccountStatus::SignedIn, Some(account), None);
    }

    pub fn set_signed_out(&mut self, provider: Provider) {
        self.update(provider, AccountStatus::SignedOut, None, None);
    }

    pub fn needs_attention(&mut self, provider: Provider, reason: &str) {
        self.update(
            provider,
            AccountStatus::NeedsAttention,
            None,
            Some(redact_message(reason)),
        );
    }

    pub fn set_plan(&mut self, provider: Provider, plan: PlanTier) -> Option<ProviderAuth> {
        let entry = self
            .entries
            .iter_mut()
            .find(|entry| entry.provider == provider)?;
        if let Some(account) = entry.account.as_mut() {
            account.plan = plan;
            account.is_lossless_capable = matches!(plan, PlanTier::HiFi | PlanTier::Premium);
        }
        Some(entry.clone())
    }

    fn update(
        &mut self,
        provider: Provider,
        status: AccountStatus,
        account: Option<AccountSummary>,
        detail: Option<String>,
    ) {
        if let Some(entry) = self
            .entries
            .iter_mut()
            .find(|entry| entry.provider == provider)
        {
            entry.status = status;
            entry.account = account;
            entry.detail = detail;
        } else {
            self.entries.push(ProviderAuth {
                provider,
                status,
                account,
                detail,
            });
        }
    }
}

/// Redact a token for logs and error surfaces: keep a short prefix, drop the rest.
///
/// `redact.rs` on the desktop does the same before anything reaches a log sink;
/// Android logs are readable through `adb logcat`, so this is not optional.
#[must_use]
pub fn redact(secret: &str) -> String {
    let trimmed = secret.trim();
    if trimmed.is_empty() {
        return "«empty»".to_owned();
    }
    let prefix: String = trimmed.chars().take(4).collect();
    format!("{prefix}…({} chars)", trimmed.chars().count())
}

/// Redact anything that looks like a long token inside a free-form message.
#[must_use]
pub fn redact_message(message: &str) -> String {
    message
        .split_whitespace()
        .map(|word| {
            let looks_like_secret = word.len() >= 20
                && word
                    .chars()
                    .all(|c| c.is_ascii_alphanumeric() || matches!(c, '-' | '_' | '.' | '='));
            if looks_like_secret {
                redact(word)
            } else {
                word.to_owned()
            }
        })
        .collect::<Vec<_>>()
        .join(" ")
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn new_store_has_both_providers_signed_out() {
        let store = AuthStore::new();
        let snapshot = store.snapshot();
        assert_eq!(snapshot.len(), 2);
        assert!(snapshot.iter().all(|e| e.status == AccountStatus::SignedOut));
    }

    #[test]
    fn sign_in_then_sign_out_clears_the_account() {
        let mut store = AuthStore::new();
        store.set_signed_in(
            Provider::Deezer,
            AccountSummary {
                id: "42".into(),
                display_name: "Listener".into(),
                plan: PlanTier::HiFi,
                is_lossless_capable: true,
            },
        );
        let auth = store.get(Provider::Deezer);
        assert_eq!(auth.status, AccountStatus::SignedIn);
        assert_eq!(auth.account.unwrap().plan, PlanTier::HiFi);

        store.set_signed_out(Provider::Deezer);
        let auth = store.get(Provider::Deezer);
        assert_eq!(auth.status, AccountStatus::SignedOut);
        assert!(auth.account.is_none());
    }

    #[test]
    fn plan_change_updates_the_capability_flag() {
        let mut store = AuthStore::new();
        store.set_signed_in(
            Provider::YouTubeMusic,
            AccountSummary {
                id: "u".into(),
                display_name: "Listener".into(),
                plan: PlanTier::Free,
                is_lossless_capable: false,
            },
        );
        let updated = store.set_plan(Provider::YouTubeMusic, PlanTier::Premium).unwrap();
        assert!(updated.account.unwrap().is_lossless_capable);
    }

    #[test]
    fn attention_reason_is_redacted() {
        let mut store = AuthStore::new();
        store.needs_attention(
            Provider::YouTubeMusic,
            "SAPISID abcdefghijklmnopqrstuvwxyz012345 expired",
        );
        let detail = store.get(Provider::YouTubeMusic).detail.unwrap();
        assert!(!detail.contains("abcdefghijklmnopqrstuvwxyz012345"));
        assert!(detail.contains("SAPISID"));
    }

    #[test]
    fn redact_keeps_a_prefix_and_length_only() {
        assert_eq!(redact(""), "«empty»");
        assert_eq!(redact("abcd1234efgh5678"), "abcd…(16 chars)");
    }
}
