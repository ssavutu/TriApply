# TriApply Portal

TriApply Portal is the public application site for The Triangle's TriApply ATS.
It renders the membership application, validates applicant input, accepts
temporary uploads, and forwards valid submissions to the separate ATS API when
`TRIAPPLY_ATS_URL` is configured. If the ATS URL is missing or submission fails,
the portal returns a service-unavailable response rather than claiming success.

## Application configuration

Application copy, questions, section choices, validation messages, and
supplemental requirements live in
`resources/triapply_portal/membership-application.edn`. It is a versioned, data-only EDN DSL;
the Clojure renderer and server validator both consume the same configuration.

Supported content blocks are `:heading`, `:paragraph`, `:ordered-list`, and
`:unordered-list`. Fields support text-like HTML input types, `:textarea`,
`:file`, and `:radio-group` with optional conditional detail fields. Restart or
reload the `triapply-portal.application-config` namespace after changing the configuration.

## Development

Install the pinned frontend build dependencies:

```sh
npm ci
```

Run the ClojureScript and CSS watchers in separate terminals:

```sh
npm run cljs:watch
npm run css:watch
```

Create optimized frontend assets:

```sh
npm run assets:release
```

Build a production uberjar. This always compiles the frontend first:

```sh
npm run build
```

Run tests:

```sh
lein test
```

Start a REPL:

```sh
lein repl
```

Run against a local ATS service:

```sh
TRIAPPLY_ATS_URL=http://localhost:4322 lein run
```

`TRIAPPLY_ATS_TIMEOUT_MILLIS` controls the ATS submission timeout and defaults
to `10000`.
