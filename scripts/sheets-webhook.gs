/**
 * Google Apps Script webhook for triapply-portal application submissions.
 *
 * Deploy:
 *   1. Open the target Google Sheet → Extensions → Apps Script.
 *   2. Paste this file, save.
 *   3. Deploy → New deployment → type "Web app".
 *        - Execute as: Me
 *        - Who has access: Anyone  (the URL is the shared secret)
 *   4. Copy the "/exec" web-app URL into TRIAPPLY_SHEET_WEBHOOK_URL.
 *
 * The portal sends one required, idempotent POST per submission. Retries carry
 * the same `submissionId`, so this script appends a row only the first time it
 * sees an id and returns 200 for subsequent duplicates.
 *
 * Columns are HEADER-DRIVEN: each answer/supplemental field gets its own column,
 * created the first time that field name is seen. So the layout adapts to the
 * form automatically — no fixed schema to keep in sync. Meta columns come first,
 * then one column per field in first-seen order.
 *
 * Headers are stored as HUMAN-READABLE labels (e.g. "Submission ID"), but the
 * code keys everything by the raw field name. `humanize_` is the single, stable
 * translation applied at the sheet boundary: every lookup converts a raw key to
 * its label before searching the header row, so display text and lookups can
 * never drift apart.
 */

var SHEET_NAME = 'Applications';
var META_HEADERS = ['submissionId', 'receivedAt', 'sections',
                    'decision', 'position', 'decidedAt'];

// Explicit labels for keys that Title-casing alone wouldn't render well.
var HEADER_LABELS = {
  submissionId: 'Submission ID',
  receivedAt: 'Received At',
  decidedAt: 'Decided At'
};

// Header row appearance. Triangle brand blue with light text.
// (Google Sheets cells can't have rounded corners — not supported by the API —
// so table "polish" comes from the colored header band + alternating row bands.)
var HEADER_BACKGROUND = '#2563eb';
var HEADER_FONT_COLOR = '#ffffff';
var BANDING_COLOR = '#eef3ff'; // Faint blue tint for every other data row.

function doPost(e) {
  var lock = LockService.getScriptLock();
  lock.waitLock(30000); // Serialize writes so concurrent requests can't collide.
  try {
    var payload = JSON.parse(e.postData.contents);
    var id = payload.submissionId;
    if (!id) {
      return json_({ ok: false, error: 'missing submissionId' });
    }
    var sheet = getSheet_();

    if (payload.type === 'decision') {
      return recordDecision_(sheet, id, payload);
    }
    return appendSubmission_(sheet, id, payload); // Default: a new application.
  } catch (err) {
    return json_({ ok: false, error: String(err) });
  } finally {
    lock.releaseLock();
  }
}

function appendSubmission_(sheet, id, payload) {
  if (findRow_(sheet, id) > 0) {
    return json_({ ok: true, duplicate: true }); // Idempotent no-op on retry.
  }

  // Collect the whole row in memory first, then write it in one shot. Per-cell
  // getRange/setValue is a spreadsheet round-trip each; a form with ~20 fields
  // would make 40+ and time out the caller. Batching keeps it to a few.
  var values = {
    submissionId: id,
    receivedAt: new Date(),
    sections: (payload.sections || []).join(', ')
  };
  collectFields_(values, payload.answers || {});
  collectFields_(values, payload.supplementals || {});

  writeRow_(sheet, sheet.getLastRow() + 1, values);
  styleHeaders_(sheet, sheet.getLastColumn()); // Keep styling fresh every write.
  autoResize_(sheet);
  return json_({ ok: true });
}

function recordDecision_(sheet, id, payload) {
  var row = findRow_(sheet, id);
  if (row < 0) {
    return json_({ ok: false, error: 'unknown submissionId' });
  }
  var headers = ensureHeaders_(sheet, ['decision', 'position', 'decidedAt']);
  setByHeader_(sheet, headers, row, 'decision', payload.decision || '');
  setByHeader_(sheet, headers, row, 'position', payload.position || '');
  setByHeader_(sheet, headers, row, 'decidedAt', new Date());
  autoResize_(sheet);
  return json_({ ok: true });
}

function collectFields_(values, obj) {
  Object.keys(obj).forEach(function (key) {
    values[key] = flatten_(obj[key]);
  });
}

// Converts a raw field key into its human-readable header label. Stable and
// deterministic so it can be used symmetrically for writes and lookups.
function humanize_(key) {
  if (HEADER_LABELS.hasOwnProperty(key)) return HEADER_LABELS[key];
  return String(key)
    .replace(/[_\-]+/g, ' ')            // snake / kebab -> spaces
    .replace(/([a-z0-9])([A-Z])/g, '$1 $2') // camelCase -> spaced words
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/\b\w/g, function (c) { return c.toUpperCase(); }); // Title Case
}

// Writes an entire row in one setValues call, creating any missing columns first.
// `values` is a raw-key -> value map; unlisted columns are left blank.
function writeRow_(sheet, row, values) {
  var headers = ensureHeaders_(sheet, Object.keys(values));
  // Re-key the values by their display label so they line up with `headers`.
  var byLabel = {};
  Object.keys(values).forEach(function (k) { byLabel[humanize_(k)] = values[k]; });
  var rowValues = headers.map(function (h) {
    return byLabel.hasOwnProperty(h) ? byLabel[h] : '';
  });
  sheet.getRange(row, 1, 1, rowValues.length).setValues([rowValues]);
}

function setByHeader_(sheet, headers, row, header, value) {
  sheet.getRange(row, headers.indexOf(humanize_(header)) + 1).setValue(value);
}

// Reads the header row once, appends any missing headers in a single write, and
// returns the full header list (as display labels) so callers can position
// values without re-reading. Restyles the header row whenever it grows.
function ensureHeaders_(sheet, needed) {
  var lastCol = sheet.getLastColumn();
  var headers = lastCol >= 1
    ? sheet.getRange(1, 1, 1, lastCol).getValues()[0].map(String)
    : [];
  var missing = [];
  needed.forEach(function (key) {
    var label = humanize_(key);
    if (headers.indexOf(label) === -1 && missing.indexOf(label) === -1) {
      missing.push(label);
    }
  });
  if (missing.length > 0) {
    sheet.getRange(1, headers.length + 1, 1, missing.length).setValues([missing]);
    headers = headers.concat(missing);
    styleHeaders_(sheet, headers.length);
  }
  return headers;
}

// Applies the header-row styling: bold light text on the brand-blue band,
// centered, and frozen so it stays visible while scrolling. Also (re)applies
// alternating row banding — the closest Sheets gets to a "polished" table,
// since rounded corners aren't a thing here.
function styleHeaders_(sheet, colCount) {
  var range = sheet.getRange(1, 1, 1, colCount);
  range
    .setFontWeight('bold')
    .setFontColor(HEADER_FONT_COLOR)
    .setBackground(HEADER_BACKGROUND)
    .setHorizontalAlignment('center')
    .setVerticalAlignment('middle');
  sheet.setFrozenRows(1);
  applyBanding_(sheet, colCount);
}

// Alternating faint-blue bands over the DATA rows (row 2 down), leaving the
// manually-styled blue header row untouched. Bandings can't overlap, so any
// existing one is removed before a fresh one is applied at the current width.
function applyBanding_(sheet, colCount) {
  sheet.getBandings().forEach(function (b) { b.remove(); });
  var rows = sheet.getMaxRows() - 1; // Everything below the header.
  if (rows < 1) return;
  sheet.getRange(2, 1, rows, colCount)
    .applyRowBanding(SpreadsheetApp.BandingTheme.LIGHT_GREY, false, false)
    .setFirstRowColor('#ffffff')
    .setSecondRowColor(BANDING_COLOR);
}

// Sizes every column to fit its widest cell, so headers and values aren't clipped.
function autoResize_(sheet) {
  var cols = sheet.getLastColumn();
  if (cols > 0) sheet.autoResizeColumns(1, cols);
}

// Renders a field value for a cell. Uploaded files (already sanitized to
// {filename, url, ...}) show their link; other objects fall back to JSON.
function flatten_(v) {
  if (v === null || v === undefined) return '';
  if (Array.isArray(v)) return v.map(flatten_).join('\n');
  if (typeof v === 'object') {
    if (v.url) return v.url;
    if (v.filename) return v.filename;
    return JSON.stringify(v);
  }
  return v;
}


function getSheet_() {
  var ss = SpreadsheetApp.getActiveSpreadsheet();
  var sheet = ss.getSheetByName(SHEET_NAME);
  if (!sheet) {
    sheet = ss.insertSheet(SHEET_NAME);
    // Seed the meta columns (as labels); fields append after.
    sheet.appendRow(META_HEADERS.map(humanize_));
    styleHeaders_(sheet, META_HEADERS.length);
  }
  return sheet;
}

// ---------------------------------------------------------------------------
// One-shot migration / re-styling for an EXISTING sheet.
//
// Run this once by hand from the Apps Script editor (select `formatSheet` in the
// function dropdown → Run) after pasting an updated script. New submissions style
// themselves, but a sheet that already has all its columns never triggers that,
// and older sheets still carry the raw `submissionId`/`files` headers — this
// fixes both in place: drops the legacy files column, rewrites headers as
// human-readable labels, then colors, bands, and auto-sizes the table.
// ---------------------------------------------------------------------------
function formatSheet() {
  // Operate on whatever tab is currently open, so it just works regardless of
  // the tab's name (and never spawns a stray empty 'Applications' tab).
  var sheet = SpreadsheetApp.getActiveSheet();
  var lastCol = sheet.getLastColumn();
  if (lastCol >= 1) {
    var headers = sheet.getRange(1, 1, 1, lastCol).getValues()[0].map(String);
    // Drop any legacy files column (raw or already-labeled), right-to-left so
    // deleting one doesn't shift the indexes of columns still to check.
    for (var c = headers.length - 1; c >= 0; c--) {
      if (headers[c] === 'files' || headers[c] === 'Files') {
        sheet.deleteColumn(c + 1);
        headers.splice(c, 1);
      }
    }
    // Normalize every remaining header to its label (idempotent on labels).
    var labels = headers.map(humanize_);
    sheet.getRange(1, 1, 1, labels.length).setValues([labels]);
  }
  styleHeaders_(sheet, sheet.getLastColumn());
  autoResize_(sheet);
}

// Returns the 1-based row number for submissionId, or -1 if not present.
function findRow_(sheet, id) {
  var last = sheet.getLastRow();
  if (last < 2) return -1; // Only the header row.
  var idCol = ensureHeaders_(sheet, ['submissionId'])
    .indexOf(humanize_('submissionId')) + 1;
  var ids = sheet.getRange(2, idCol, last - 1, 1).getValues();
  for (var i = 0; i < ids.length; i++) {
    if (String(ids[i][0]) === String(id)) return i + 2; // +2: skip header, 1-based.
  }
  return -1;
}

function json_(obj) {
  // Apps Script web apps always return HTTP 200; the portal treats any 2xx as
  // accepted, so the `ok` flag in the body is the real signal for humans.
  return ContentService
    .createTextOutput(JSON.stringify(obj))
    .setMimeType(ContentService.MimeType.JSON);
}
