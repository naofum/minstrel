# Minstrel Windows - Regression Tests

End-to-end smoke tests for the Minstrel Windows web app, run against the real
Java server + the real book data in `data/minstrel/`, in a headless Edge/Chrome
browser via puppeteer-core.

The suite covers every phase of the de-Cordova / de-jQuery-Mobile / de-jQuery
port, verifying that the app still works without jQuery (mini-jquery.js,
jquery-mobile-shim.js, mini-spectrum.js, modern.css):

- **Library**: scans `data/minstrel/`, lists the EPUB/CBZ/ABZ books, search box filters them
- **Icons**: footer and split-button icons render (Font Awesome glyphs)
- **Settings**: collapsible expands/collapses, checkbox enhanced, localized
  heading (ja), back button navigates to the library
- **EPUB reader**: opens, renders content, slider present, quickbar icons render,
  lock button toggles
- **CBZ reader**: page image loads
- **ABZ reader**: track list populates
- **Color picker**: spectrum popup opens, swatches render, OK applies and closes
- **Console**: zero console/page errors and zero HTTP 4xx across the whole run

## Requirements

- JDK 21+ and Maven (to build `server/target/minstrel-server.jar`)
- Node.js (test runner)
- Microsoft Edge or Google Chrome installed

## Usage

First run (installs puppeteer-core into `test/node_modules`):

    powershell -ExecutionPolicy Bypass -File test\run-tests.ps1

The script:

1. builds the server with Maven,
2. starts `minstrel-server.jar` on port 8001 (web = `web/`, data = `data/`),
3. runs `node regression.js`,
4. stops the server.

Exit code is 0 when all checks pass, 1 otherwise. Screenshots are saved to
`test/shots/`.

### Run manually

    # start the server yourself, then:
    npm install
    $env:BASE_URL = "http://127.0.0.1:8001/"
    node regression.js

`regression.js` accepts an optional base URL and shots directory as arguments:

    node regression.js http://127.0.0.1:8001 C:\tmp\shots

## Notes

- The regression requires the sample data present under `data/minstrel/`
  (A Christmas Carol, Menestrello Guide, Sample Audiobook, Sample Comic,
  The Piazza). Regenerate them with `data/gen_epub3.ps1` / `data/gen_sample.ps1`
  if missing.
- The Java server unit tests are independent of this suite; run them with
  `mvn test` in `server/`.