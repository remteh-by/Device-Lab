# Device Lab session family v1

All reports use an integer schema=1, a kind discriminator, a random local session_id, application/version, device description without serial, source, a timestamp, comment and explicit measurement limitations. Consumers must select the kind adapter before interpreting numeric fields. Do not treat one kind's numbers as another kind's test.

- controller: Android API axes/ranges; started_at_ms; duration_ms; timezone; raw axes and resting_axes with samples/min/max/mean/standard_deviation/last; display_dead_zone only affects rendering; recent_samples are bounded to 256. No hardware calibration or true latency claim.
- mouse: browser events; started_at ISO timestamp; duration_ms/timezone; conditions and summary; observations bounded to 512; source pointerrawupdate/pointermove; eventRateHz is not USB polling. Missing observations are null, not guessed defaults.
- phone-intake: manual Android intake; started_at_ms/timezone; checks with status, source and observation time. No media, account or serial is exported. Status is operator observation, not an automatic certificate of hardware health.

Current statuses: not-measured, observed, suspicion, unavailable. Imported reports are never executable instructions. Reject unsupported schema/kind, oversized files, invalid numeric types/ranges and excessive nesting; render text with textContent. JSON is not a source of HTML. Unknown future formats must produce an explanation instead of coercion.

The offline viewer opens up to two v1 reports, preserves current documents on an invalid import and offers a printable client view. Different kind/source/conditions make direct comparison invalid or limited. A named device is user-provided, not authenticated hardware identity. JSON export retains original observations; the viewer does not recalculate a diagnosis.

Controller and mouse can link previous_session_id explicitly. PhoneCheck MVP shows bounded history but does not yet implement before/after editing or import. Optional repair order reference and full cross-app editing are deferred; CRM integration is excluded from this task. Do not add hidden identifiers or credentials to public code.

A separate unified product/release is a later phase. This workspace reuses a canonical Java diagnostics core and synchronizes the thin Android adapter into the three existing APK projects, preserving their package names and licensing.
