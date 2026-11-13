# Changelog

<!--
Telegram: end each version's section with a hidden block for the StillCraft chat. Start it with a
line "<!-- tg", then 2-4 short lines in Russian for users (what is new, what is fixed), no markdown,
then close it like any HTML comment. The release notes carry it, and the chat bot posts those lines.
-->

All notable changes to DearMe. Versions follow [semantic versioning](https://semver.org/).

## [1.0.4] - 2026-11-13

### Fixed
- Narrow phones with a large system font (#1): the "Write" button and the countdown shrink to stay on one line instead of being cut off or wrapping; a sealed letter's title and opening date in the list may take two lines; the bottom bar and the segmented switches grow with the font.

<!-- tg
Исправлено: на узких экранах с крупным шрифтом кнопка «Написать» и таймер больше не обрезаются, а названия писем в списке видны целиком.
-->

## [1.0.3] - 2026-11-08

### Added
- Settings -> Community has a "Support DearMe" line: it opens the StillCraft support page, for new envelopes and letters across the years.

<!-- tg skip -->

## [1.0.2] - 2026-10-25

### Added
- Community links in settings: report a bug, suggest an idea or follow updates through the StillCraft bot on Telegram, and the StillCraft chat.

<!-- tg skip -->

## [1.0.1] - 2026-10-17

### Fixed
- A ritual calls at most once a day. A random moment used to pick its next time later the same day, so it could call again and again until its window closed.

### Changed
- "Step by step" now says where it stands: how long moments stay sealed for now and from which day the wait grows (a week, then a month, then a year).

## [1.0.0] - 2026-10-15

The first release.

### Added
- **Letters to your future self**: text, photos (back camera, selfie, both at once, or from the gallery) and a voice note of up to a minute, sealed for a week, a month, 1, 5, 10 or 30 years, or until any date you pick.
- **Rituals** that call you to leave something for later: a random time inside a window or an exact time; chosen weekdays, a day of the month or a day of the year; photo mode, a line or a letter, voice, a question, a two-minute timer, and a preset or custom time until opening.
- **Random moments**, BeReal-style: a dual shot (back camera, then a selfie) and one line within two minutes; late answers are stamped. Moments open step by step: after a week at first, then a month, then a year.
- **Breaking the seal**: a countdown while sealed, the wax cracking and the flap lifting when the day comes, a notification when it does.
- **Then & now** shots, **replies** to your past self, and **share cards** for a sealed letter, an opened one and then & now.
- 40 built-in questions, English and Russian throughout, light and dark themes.
- A clock check against time since boot and the network clock, so changing the date doesn't open anything.
- A single-file `.dearme` backup with every letter, photo and voice note, scrambled inside.
