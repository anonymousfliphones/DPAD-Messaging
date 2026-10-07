# UI Visual Baseline

This is the reference checklist for visual-only UI work. It covers the compact
D-pad layouts used by DPAD Messaging and intentionally excludes behavior,
navigation order, message handling, and data changes.

## Capture Matrix

Capture the following screens at 240x320 and 320x480 when hardware or an
emulator is available:

- Conversation list with unread, pinned, muted, and long-preview rows.
- Thread with incoming and outgoing bubbles, timestamps, metadata, and attachments.
- Compose area with empty, populated, and scheduled-message states.
- Settings with section headers, toggles, summaries, and focused rows.
- Conversation details with participant and action rows.
- Empty, loading, retry, archived, and recycle-bin states.
- Dialogs, menus, image viewer, and selection toolbar.

Repeat the matrix for:

- Light and dark themes.
- Blue, green, orange, and rose accents.
- Default and enlarged UI scale.
- D-pad focus on each interactive surface.

## Visual Assertions

- The current D-pad target is immediately visible without relying on touch ripples.
- Primary text is clearly stronger than previews, dates, and status metadata.
- Surface changes are visible without heavy shadows or large color jumps.
- Sent and received messages remain distinct in every theme and accent.
- Dates, badges, and indicators do not crowd contact names or previews.
- Text and controls remain legible at the smallest supported display size.
- Dialogs, menus, settings, and empty states share the same color and spacing language.
- No visual change alters focus order, click behavior, message handling, or navigation.

## Phase 1 Token Roles

The primary visual roles are defined in `app/src/main/res/values/colors.xml`
and `values-night/colors.xml`:

- `surface_background`: application background.
- `surface_base`: primary content surface.
- `surface_elevated`: menus and raised content.
- `text_primary`, `text_secondary`, `text_tertiary`: readable hierarchy.
- `divider_subtle`: low-contrast separation.
- `focus_fill`, `focus_outline`: D-pad feedback.
- `status_success`, `status_error`: message status semantics.

Legacy resource names remain as aliases so this token pass does not change
behavior or require a broad layout migration.
