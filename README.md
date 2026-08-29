# Minstrel (modernized edition)

**Minstrel** is a FLOSS hybrid reading app designed for Audio-eBooks.

This repository is a **modernized continuation** of the original
[Minstrel][webpage] by [ReadBeyond][rb] / [Alberto Pettarin][ap],
with great thanks to the original authors for their wonderful work.
It keeps the same reader engine and UI, but replaces the legacy
Cordova build with a current architecture:

* the **same `web/` codebase** is shared by all platforms,
* a small **Java backend** (JDK `HttpServer`, no dependencies) serves
  the app for Windows/PC development,
* the **Android app** is rebuilt with **Capacitor** and native plugins
  (audio playback now uses **androidx.media3 / ExoPlayer**).

* Version: 4.0.0
* Author: naofum@gmail.com
* Original author: [Alberto Pettarin][ap] / [ReadBeyond][rb]
* License: MIT
* Original Web Page: [Minstrel][webpage]

---

## Repository layout

```
minstrel/
  server/   Java 21 (Maven) backend for Windows/PC development (JDK HttpServer)
  web/      Shared UI - single source of truth used by all platforms
  android/  Capacitor 8 Android app (package com.github.naofum.minstrel)
  test/     End-to-end regression tests (Puppeteer)
  data/     Runtime data / sample books (minstrel/...)
```

The platform differences are confined to a thin transport layer:

* in a browser the app talks to the local Java server over HTTP
  (`/api/commander`, `/api/librarian`, `/api/unzipper`),
* on Android it talks to the native Capacitor plugins
  (`Commander`, `Librarian`, `Unzipper`, `Media`).

---

## Building and Running

### System Requirements

* **Java 21** (JDK) and **Maven** (for the Windows server)
* **Node.js / npm** (for Capacitor and the regression tests)
* **Android SDK** (for the Android app)
* JDK 17+ is required by the Android Gradle plugin

### Windows / PC (web app in a browser)

1. Clone the repository:

    ```bash
    $ git clone https://github.com/naofum/minstrel.git
    $ cd minstrel
    ```

2. Put your EPUB / CBZ / ABZ files into `data/minstrel/`.

3. Build the Java server and start the app:

    ```bash
    $ powershell -File run.ps1
    ```

    (or `run.bat`). This runs `mvn package`, starts the server at
    `http://127.0.0.1:8000/`, and opens the app in your browser.
    Press **"Scan default directory"** on first run to import your books.

### Android (Capacitor app)

1. Install dependencies and sync the shared `web/` assets:

    ```bash
    $ cd android
    $ npm install
    $ npx cap sync android
    ```

2. Build the debug APK from the command line:

    ```bash
    $ cd android/android
    $ ./gradlew assembleDebug
    # APK: android/android/app/build/outputs/apk/debug/app-debug.apk
    ```

    or open the project in Android Studio and run it:

    ```bash
    $ cd android
    $ npx cap open android
    ```

3. To run on a connected device/emulator:

    ```bash
    $ npx cap run android
    ```

> The Android app loads the shared `web/` code from its assets
> (`npx cap sync` copies it). Data is stored in the app's external
> files directory (`.../com.github.naofum.minstrel/files/minstrel/`).

### Regression tests (Windows web app)

Start the Java server, then:

```bash
$ cd test
$ npm install
$ npm test
```

---

## Features

* Supported formats: EPUB 2, EPUB 3 (reflowable and pre-paginated), CBZ, ABZ
* Multilingual UI: EN, IT, DA, FR, DE, PL, ES, TR, JA
* Import documents from the file system / library scanning
* Library sorted by: author, title, narrator, duration, language, series, or recently open
* Library titles can be filtered; book metadata is displayed
* Reading settings saved per book, and globally customizable defaults
* Night mode, customizable colors, orientation lock, screen brightness
* Several reading fonts, including OpenDyslexic and TestMe fonts for dyslexic people
* Customizable touch zones
* Incremental unzipping of assets
* Nearly complete support for EPUB 3 Media Overlays (SMIL / read aloud)
* Tap-text-to-play-it, synchronous highlighting, autoscroll
* Keep playing audio while the app is in background
* Adjustable playback speed with rate-vs-pitch correction (0.5x-2.0x)
* Smart footnotes
* Image zooming
* Support for non linear contents
* Ignore book CSS, user-provided CSS overrules
* Enable/disable Javascript execution
* Presentation mode and image info in CBZ, M3U support in ABZ
* IPA Mincho font (Japanese Edition)

### What changed in this edition

* **Audio engine**: the Android app now plays audio with
  **androidx.media3 (ExoPlayer)** instead of the legacy
  `MediaPlayer` + Sonic `.so`. Playback speed (0.5x-2.0x) is built-in,
  so no native Sonic library is needed.
* **No Cordova**: the Android app is a **Capacitor** project with small
  native plugins; the shared UI is served from the app assets.
* **Windows/PC development**: a zero-dependency **Java HTTP server**
  serves the same `web/` app in a browser, so the reader can be
  developed and tested without an Android device.
* **Modernized front-end**: the legacy jQuery Mobile / jQuery
  dependencies were replaced by small self-contained shims
  (`jquery-mobile-shim.js`, `mini-jquery.js`, `mini-spectrum.js`),
  reducing dependency risk while keeping the same look and behavior.

---

## Libraries and Tools

### Front-end (`web/`)

* [Hammer.js][hammer] - MIT (touch gestures)
* [sprintf.js][sprintf] - unrestricted (string formatting)
* [cssbeautify.js][cssbeautify] - MIT
* `panzoom.min.js` - MIT (image zoom)
* `mini-jquery.js` - jQuery-compatible shim (this project, MIT)
* `jquery-mobile-shim.js` - jQuery Mobile shim (this project, MIT)
* `mini-spectrum.js` - color-picker shim (this project, MIT)
* [Font Awesome][fontawesome] - OFL
* [nativeDroid][nativedroid] theme (UI styling)

### Backend (`server/`)

* JDK 21 [`HttpServer`][jdkserver] - built into the JDK, no runtime dependencies
* [Maven][maven] + [JUnit 5][junit] (build and tests)

### Android (`android/`)

* [Capacitor 8][capacitor] - Apache 2.0
* [androidx.media3 / ExoPlayer][media3] 1.11.0 - audio playback
* [androidx.core:core-splashscreen][core_splashscreen]
* AndroidX AppCompat / CoordinatorLayout

### Tests (`test/`)

* [Puppeteer][puppeteer] - end-to-end browser tests

---

## Documentation

The main source of documentation is the
[official Web page][webpage] of the project and the comments in the
source code. Thanks to the modernized structure, the code is split
into small, focused components (`server/`, `web/`, `android/`, `test/`)
that are easier to navigate than the original monolithic Cordova tree.

---

## TODO / Future Work

* Full text search, dictionary/vocabulary building, annotations
* Downloading documents directly inside the app
* User-provided fonts
* OPDS support
* Better pagination, support for facing FXL pages and parallel texts
* MediaSessionService-based lock screen / media notification controls
  (playback already continues in background)
* Formalized tests for the Android app
* The reader engine still carries legacy quirks (monkey-patching of
  injected XHTML) that a future `<iframe>`-based renderer would remove

---

## Supporting and Contributing

We are grateful to the original authors and welcome contributors.

If you are able to contribute code directly, feel free to open a pull
request. Please make your code consistent with the existing code base
style and test your code before opening the pull request.

Any contribution adding or altering the JS/native plugin interfaces
should work for both the browser (HTTP) and Android (Capacitor)
backends, since the `web/` layer is shared.

If you think you found a bug, please use the
[GitHub issue tracker](https://github.com/naofum/minstrel/issues)
to file a bug report.

**Please note that, by opening a pull request, you automatically agree
to apply the MIT license to the code you contribute.**

---

## Legal Information

### License

**Minstrel** is released under the terms of the MIT License.
See the [LICENSE](LICENSE) file for details.

### Privacy Policy

We value your privacy as much as we value ours.
We do not collect any data about your eBooks, reading preferences,
or reading statistics.

* On Android, the app renders eBooks in a WebView and plays audio with
  the built-in media engine; it does not send data to or receive data
  from the Internet.
* On Windows/PC, the app is served by a local `http://127.0.0.1` server
  for development; it does not contact the network.

### Font Licenses

#### Amaranth

Copyright © 2011, Gesine Todt (hallo@gesine-todt.de), with Reserved Font Name Amaranth. This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Andika

Copyright © 2004-2011, SIL International (http://scripts.sil.org), with Reserved Font Names 'Andika' and 'SIL'. This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Avería Serif

Copyright © 2011, Dan Sayers (i@iotic.com), with Reserved Font Name Avería Serif. This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Charis SIL

This Font Software is Copyright © 1997-2011, SIL International (http://scripts.sil.org/) with Reserved Font Names "Charis" and "SIL". This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Font Awesome

Copyright © 2014, Dave Gandy (http://fontawesome.io), with Reserved Font Name Font Awesome. This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Gentium

Copyright © 2003-2008, SIL International (http://scripts.sil.org), with Reserved Font Names "Gentium" and "SIL". This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### EB Garamond

Copyright © 2010, 2011, 2012 Georg Duffner (http://www.georgduffner.at). This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### IPA Font

Copyright © CITPC (https://moji.or.jp/ipafont). This Font Software is licensed under the IPA Font License, Version 1.0.

Web: [https://moji.or.jp/ipafont/license/][ipa_fonts]


#### OpenDyslexic Font

Copying is an act of love. Please copy. OpenDyslexic(open-dyslexic) by Abelardo Gonzalez is licensed under a Creative Commons Attribution 3.0 Unported License. Based on a work at dyslexicfonts.com.

Web: [http://opendyslexic.org/][open_dyslexic_font]


#### Roboto

Copyright © 2012 Christian Robertson (https://plus.google.com/110879635926653430880/about). This Font Software is licensed under the Apache License, Version 2.0.

Web: [http://www.google.com/fonts/specimen/Roboto][roboto]


#### TestMe

Copyright © 2013 Luciano Perondi (www.synsemia.org|molotro@gmail.com). Derived from Titillium Copyright © 2008-2010, Accademia di Belle Arti di Urbino (www.campivisivi.net|direzione@accademiadiurbino.it), with Reserved Font Name TestMe. This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


#### Volkhov

Copyright © 2011, Cyreal (www.cyreal.org), with Reserved Font Name "Volkhov". This Font Software is licensed under the SIL Open Font License, Version 1.1.

Web: [http://scripts.sil.org/OFL][sil_ofl_fonts]


### Trademarks

`EPUB` is a registered trademark of the International Digital Publishing Forum (IDPF).

Web: [http://idpf.org/][epub]

---

## Acknowledgments

We express our deep gratitude to the original author **Alberto Pettarin**
and **ReadBeyond** for creating Minstrel and releasing it as FLOSS.
This modernized edition is built directly on their work and would not
exist without them.

Additional thanks from the original project:

* Antonio Tombolini and the technical staff of Simplicissimus Book Farm provided many useful comments.
* Marta D'Asaro designed the icon.
* Iacopo Balocco suggested embedding the OpenDyslexic font.
* Carlo Fantozzi and Nicola Zago suggested several UI enhancements.
* Several users of the SBF forum provided precious feedback.
* Marco Iannacone suggested embedding the TestMe font.
* Fabrizio Venerandi inspired the EPUB reader options for dealing with non linear spine items.

[rb]: http://www.readbeyond.it/
[ap]: http://www.albertopettarin.it/
[webpage]: http://www.readbeyond.it/minstrel/
[epub]: http://idpf.org/
[hammer]: https://github.com/EightMedia/hammer.js
[sprintf]: https://github.com/alexei/sprintf.js
[cssbeautify]: http://cssbeautify.com/
[fontawesome]: http://fontawesome.io/
[nativedroid]: http://nativedroid.godesign.ch/
[open_dyslexic_font]: http://opendyslexic.org/
[roboto]: http://www.google.com/fonts/specimen/Roboto
[sil_ofl_fonts]: http://scripts.sil.org/OFL
[ipa_fonts]: https://moji.or.jp/ipafont/license/
[jdkserver]: https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/module-summary.html
[maven]: https://maven.apache.org/
[junit]: https://junit.org/junit5/
[capacitor]: https://capacitorjs.com/
[media3]: https://developer.android.com/media/media3/exoplayer
[core_splashscreen]: https://developer.android.com/develop/ui/views/launch/splash-screen
[puppeteer]: https://pptr.dev/