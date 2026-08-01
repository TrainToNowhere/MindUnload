# MindUnload

A personal AI organizer for Android: speak or type whatever is on your mind — the app turns it into structured tasks, appointments, shopping lists, ideas and notes.

## What problem does it solve?

Classic to-do and calendar apps expect you to sort everything yourself: type a title, pick a category, set a date, create a reminder. That is exactly why capturing thoughts fails in everyday life — it is too cumbersome, so it simply doesn't happen.

MindUnload flips this around: **capturing is a single sentence**, by voice or text, straight from the home-screen widget or as a share target from other apps. An AI (Claude) breaks the input down, detects type, date, priority and category, creates reminders and links related entries. The app does the organizing — not you.

## What can it do?

- **Capture by voice/text**: "Buy milk and bread, dentist Tuesday at 10, tax return is urgent" → shopping list, appointment with reminder and a high-priority task, all in one go.
- **Change by voice**: "Check off milk", "Move the haircut to Friday", "Turn that appointment into a weekly series" — no forms involved.
- **Appointments & reminders**: overview grouped by month and week (free days at a glance), recurring appointments, alarm notifications.
- **Morning briefing**: daily notification with the day's overview, weather and suggestions from the backlog.
- **Ask & knowledge**: the "Ask" chat mode answers questions about your own data; a wiki collects everyday knowledge, and research results (with real web search) can be adopted into it.
- **Cleanup & resurfacing**: weekly cleanup suggestions (duplicates, long-done entries) and gentle resurfacing of items that have been lying around.
- **Photos & voice messages**: attach a photo in the chat (note, receipt, poster) and Claude reads out its text before processing it; record a voice message, play it back in the chat, and it is transcribed on the device (whisper.cpp, offline) and processed afterwards.
- **Extras**: weekly/monthly reviews, JSON export/import, API cost dashboard, German + English.

## Tech (in short)

- Android (Kotlin, Jetpack Compose, Room, WorkManager), no server component of its own.
- Claude API via the Anthropic Java SDK; a cheap model for parsing and photo transcription, a stronger one for research and reviews.
- Speech recognition runs locally via a bundled whisper.cpp build (`app/src/main/cpp`); the model is downloaded once from the settings, the audio never leaves the phone.
- Outbox pattern: capturing works offline too — processing happens as soon as there is a network.
- Requires your own Claude API key (entered in the settings, stored encrypted).

Details on the full feature set live in [PRODUKT.md](PRODUKT.md).
