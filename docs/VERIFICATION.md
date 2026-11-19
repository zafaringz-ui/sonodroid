# Prototype status and verification

This is scaffolding, not a completed implementation. No Gradle sync, Android or Rust
compilation, unit tests, instrumentation tests, device tests or benchmarks have been
run in the authoring environment. XML and TOML parsing and a delimiter-balance check
were run; they do not establish correctness. The CI workflow and native build scripts
are untested. No Gradle wrapper binary is present.

## Required next work

1. Obtain and audit the upstream Sonora v0.50.2 source and its license/dependencies;
   integrate its provider and audio components instead of the current fixture core.
2. Compile the default Gradle project and Rust crate; fix all errors and run their
   tests. Generate UniFFI 0.28 bindings, reconcile every mapping and callback with
   the actual generated API, and test both builds on all supported ABIs. The opt-in
   adapter currently contains unverified assumptions and may not compile.
3. Implement and validate provider sign-in, secure credential lifecycle and sign-out
   before enabling real credentials. Review the WebView host restrictions, cookie
   clearing, vault failure handling, logging redaction and network trust strategy.
   The current sign-in screen only supports demo accounts.
4. Implement licensed provider streaming, real BotGuard/PoToken integration, an
   Oboe-backed audio engine, queue/session lifecycle, real network handling and
   encrypted Deezer offline storage. Never implement YouTube audio downloads.
5. Complete the 38-screen inventory and shared features in the supplied plan; many
   current screens are partial and simulated. Validate quality ceilings against
   actual providers rather than the fictional demo policy.
6. Verify accessibility (TalkBack, 48 dp targets, 200% font scaling), TLS/security,
   signing, GPL obligations, supported devices, playback gaplessness, start/seek
   latency, battery, APK size and crash-free rates on real devices. None of the
   plan's quantitative targets are demonstrated by the scaffold.

See `PLAN.md` and `plans/` for the original acceptance criteria and scope decisions.
