# FocusTube: YouTube without the distractions

FocusTube is an Android app for learning from YouTube without getting pulled into
entertainment. During **focus time** (a session you start, or a weekly schedule) it:

1. **Blocks the YouTube app and youtube.com** in common browsers, and shows a
   "Stay focused" screen instead.
2. **Lets you learn from YouTube inside FocusTube.** You can search for lessons
   or share a YouTube link to the app. A video only plays if it passes your
   *educational filter*.

> 📚 **New to Android?** Read [`docs/LEARN_ANDROID_DESIGN.md`](docs/LEARN_ANDROID_DESIGN.md).
> It uses this app to show how an Android app is designed, from the idea to the code.

---

## Features

| Feature | How it works |
|---|---|
| Focus sessions | Pick 25 min to 4 h. Starting again while a session is running only makes it longer. |
| Weekly schedule | For example Mon–Fri 09:00–17:00. Windows that cross midnight, such as 22:00–02:00, also work. |
| Strict mode | While focus is on, you can't end the session early or loosen your filters. You can still add blocked words. |
| Educational filter | Uses YouTube's video **category**, plus blocked words, trusted channels, and Shorts blocking. |
| App and website blocker | An Accessibility service blocks the YouTube app, YouTube Kids, popular third-party YouTube apps, and youtube.com in Chrome, Firefox, Edge, Brave, Samsung Internet, Opera and DuckDuckGo. |
| Share to FocusTube | Share any YouTube link to FocusTube. It plays only if it's educational. |

### How the educational filter decides

Each video goes through these checks in order (`domain/ContentFilter.kt`):

1. **Shorts** (60 seconds or less, or tagged `#shorts`) are blocked. You can turn this off.
2. Videos from a **trusted channel** are allowed.
3. Videos with a **blocked word** in the title or tags are blocked. The defaults
   include *prank, meme, reaction, vlog, gameplay* and similar words.
4. Otherwise the video's **category** must be one you allowed. The defaults are
   *Education*, *Science & Technology* and *Howto & Style*.

Creators pick the category themselves, so the filter isn't perfect. Some good
teachers upload under "People & Blogs". When one of their videos is blocked, tap
**Trust *channel*** on its page.

---

## Getting started

### 1. Install the tools
- [Android Studio](https://developer.android.com/studio) (latest stable version)
- An Android phone (Android 8.0 or newer) with
  [USB debugging](https://developer.android.com/studio/debug/dev-options), or an emulator

### 2. Get a free YouTube Data API key
FocusTube searches YouTube and reads video categories through the official API.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/) and create a project.
2. Open **APIs & Services → Library**, search for **YouTube Data API v3**, and click **Enable**.
3. Open **APIs & Services → Credentials → Create credentials → API key**.
4. Recommended: click the key and, under **API restrictions**, limit it to *YouTube Data API v3*.

The free quota is 10,000 units per day. One search uses about 101 units, so you
get roughly 100 searches a day. Opening a shared link uses 1 unit.

### 3. Add the key and run
Open the project in Android Studio. Then add this line to the `local.properties`
file in the project root. Android Studio creates that file, and git ignores it,
so the key is never committed.

```properties
YOUTUBE_API_KEY=AIza...your key...
```

Press **Run ▶**. You can also paste the key later under **Settings → YouTube API key**.

### 4. Turn on the blocker
In the app, tap **Set up blocker** and then **Open settings**. Find **FocusTube**
under *Accessibility* (on some phones it's under *Installed apps* or
*Downloaded apps*) and switch it on.

> On Android 13 and newer, apps installed from outside the Play Store may show
> "Restricted setting". To allow it, go to **Settings → Apps → FocusTube → ⋮ →
> Allow restricted settings**, then try again.

### Run the unit tests
```bash
./gradlew test
```
The filter, schedule and link-parsing logic is plain Kotlin, so these tests run
on your computer without a phone.

---

## Project structure

```
app/src/main/java/com/focustube/app/
├── FocusTubeApp.kt            Application + AppContainer (builds shared objects)
├── MainActivity.kt            Single activity, receives shared YouTube links
├── domain/                    Pure Kotlin rules, unit-tested
│   ├── ContentFilter.kt       Is this video educational?
│   ├── FocusRules.kt          Is focus on right now? Until when?
│   ├── YouTubeLinks.kt        Find video IDs in links, spot youtube.com URLs
│   └── Durations.kt           Parse "PT15M33S" durations
├── data/
│   ├── SettingsRepository.kt  Saves settings with DataStore, enforces strict mode
│   └── youtube/               Retrofit API + VideoRepository
├── blocker/
│   ├── BlockerAccessibilityService.kt   Detects YouTube during focus
│   └── BlockedActivity.kt               "Stay focused" screen
└── ui/                        Jetpack Compose screens + ViewModels
    ├── focus/  learn/  player/  settings/
    ├── components/            Shared UI pieces
    └── theme/                 Material 3 colours
```

## Limitations

- **The category is chosen by the uploader.** Blocked words and trusted channels
  make up for this, but some videos will still be classified wrongly.
- **Determined users can get around it.** Turning off the Accessibility service,
  using a browser that isn't listed, or uninstalling the app all work. Strict mode
  is meant to help your willpower, not replace parental controls. For that, see
  Google Family Link.
- **Google Play policy.** Apps that use the Accessibility API must explain why
  (FocusTube shows that explanation before opening settings) and fill in a
  declaration form when publishing.
- Videos play through YouTube's official embedded player, so ads and YouTube's
  terms still apply. Some videos don't allow embedding, and searches skip those.
