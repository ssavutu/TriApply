# TriApply

TriApply is a minimal ATS solution for The Triangle. It is intentionally smaller
than general-purpose applicant tracking systems, providing a cleaner,
self-hosted alternative to the current Google Form and Slack-based application
pipeline.

The app is intended to provide an applicant form, reviewer dashboard, candidate
messaging, and editable form options. All review is done by hand, without AI, as
preferred by The Triangle staff.

## Development

Install the ClojureScript build dependencies:

```sh
npm install
```

Compile the browser code and rebuild it when a ClojureScript source file changes:

```sh
npm run cljs:watch
```

Create an optimized browser build:

```sh
npm run cljs:release
```

Run tests:

```sh
lein test
```

Start a REPL:

```sh
lein repl
```
