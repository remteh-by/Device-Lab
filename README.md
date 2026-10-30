# RemTeh Device Lab foundation

Canonical Java measurement core and Android adapter used by dualsense-tester, xbox-tester and joycon-tester. Raw API values, sample counts, explicit rest windows and event intervals are separate from visual filters. The existing proprietary source license is retained; no license migration is implied.

Run tools/sync-testers.py to update the three sibling repositories, then tools/sync-testers.py --check. Each vendored copy contains SOURCE.json hashes. Unit tests live with the core and run through each Android project's Gradle build. Package IDs and signing identities remain independent.

Open viewer/index.html directly in a browser for a local, network-free report viewer. It imports two validated reports, labels differing sources/conditions and prints a readable report. See SESSION-FORMAT.md for the versioned report family and its limitations. A report is not a hardware health certificate.

phonecheck/ is a separate Android intake MVP (0.1.0 debug candidate), with a specification and physical test matrix in its README. It has no Internet permission and no CRM integration. A public product/repository/license choice is a later release step; this local workspace does not claim that step has happened.

Viewer regression: `npm ci`, `npx playwright install chromium`, `npm test`. Synthetic examples cover all three report adapters, invalid types, script-like text and 390/1920/2560/3840px. No network or real client data is needed.
