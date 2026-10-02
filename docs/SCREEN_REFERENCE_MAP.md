# Owner-supplied visual reference mapping (seven portrait mockups)

Use owner-supplied images as *visual references only*: do not ship them as app images or insert their embedded object photographs as default example evidence. Show the user's actual captured media; a placeholder is used before capture. Maintain the English-first four-language copy and mirror horizontal order in Arabic RTL.

1. **Operations home** — airy neutral background, dark navy header, teal rounded main CTA, three neutral filters, large white operation cards with a 100dp+ photo thumbnail, colored state chips, *descriptive* counts. Current Compose navigation: `HomeScreen`.
2. **New documentation** — multi-card category picker, icon and short explanation, unmistakable selected border, persistent Continue. Current Compose `CreateScreen` combines the optional form below the picker; split into two steps only after usability testing.
3. **Annotation** — tall image, marker tools and text box below. Current `ReviewScreen` supports separate-data circle annotations; rectangle and arrow, correct image-space geometry, rotation and editing are release gates.
4. **Guided camera** — clean full-screen camera with navy header/footer, framing guides, progress and flash. Current `CameraScreen` uses CameraX plus adjustable manual first-session photo overlay for return. Do not promise pixel-perfect alignment.
5. **Before/after** — both photos occupy clear equal-height cards, switchable single-photo mode, explicit user-provided assessment. Current `ComparisonScreen` supports manual initial/return image linking for multiple images; zoom/pan remain release gates.
6. **PDF report** — modern A4-style preview, page controls, summary/detailed and privacy options. Current `ReportScreen`, `PdfPreviewScreen` and `ReportMaker` include page-by-page preview. Arabic font embedding and PDF visual tests must pass before store publication.
7. **Operation detail** — hero thumbnail, documented/skipped/pending progress, summary/initial/return/comparison/report sections, expandable checklist. Current `DetailScreen` and `SessionScreen` use grouped card components; expand and responsive-device polish need real screenshot audits.

Design tokens reside in `ui/Theme.kt` and `docs/DESIGN_SYSTEM.md`. Do not use perceived completeness percentages as a professional inspection score; label exact captured/skipped/N/A/pending quantities.
