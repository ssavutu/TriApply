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
 */

var SHEET_NAME = 'Applications';
var META_HEADERS = ['submissionId', 'receivedAt', 'sections', 'files',
                    'decision', 'position', 'decidedAt'];

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
    sections: (payload.sections || []).join(', '),
    // One clickable URL per line; Google Sheets auto-links bare URLs.
    files: (payload.files || []).map(function (f) { return f.url; }).join('\n')
  };
  collectFields_(values, payload.answers || {});
  collectFields_(values, payload.supplementals || {});

  writeRow_(sheet, sheet.getLastRow() + 1, values);
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
  return json_({ ok: true });
}

function collectFields_(values, obj) {
  Object.keys(obj).forEach(function (key) {
    values[key] = flatten_(obj[key]);
  });
}

// Writes an entire row in one setValues call, creating any missing columns first.
// `values` is a header -> value map; unlisted columns are left blank.
function writeRow_(sheet, row, values) {
  var headers = ensureHeaders_(sheet, Object.keys(values));
  var rowValues = headers.map(function (h) {
    return values.hasOwnProperty(h) ? values[h] : '';
  });
  sheet.getRange(row, 1, 1, rowValues.length).setValues([rowValues]);
}

function setByHeader_(sheet, headers, row, header, value) {
  sheet.getRange(row, headers.indexOf(header) + 1).setValue(value);
}

// Reads the header row once, appends any missing headers in a single write, and
// returns the full header list so callers can position values without re-reading.
function ensureHeaders_(sheet, needed) {
  var lastCol = sheet.getLastColumn();
  var headers = lastCol >= 1
    ? sheet.getRange(1, 1, 1, lastCol).getValues()[0].map(String)
    : [];
  var missing = [];
  needed.forEach(function (h) {
    if (headers.indexOf(h) === -1 && missing.indexOf(h) === -1) missing.push(h);
  });
  if (missing.length > 0) {
    sheet.getRange(1, headers.length + 1, 1, missing.length).setValues([missing]);
    headers = headers.concat(missing);
  }
  return headers;
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
    sheet.appendRow(META_HEADERS); // Seed the meta columns; fields append after.
  }
  return sheet;
}

// Returns the 1-based row number for submissionId, or -1 if not present.
function findRow_(sheet, id) {
  var last = sheet.getLastRow();
  if (last < 2) return -1; // Only the header row.
  var idCol = ensureHeaders_(sheet, ['submissionId']).indexOf('submissionId') + 1;
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
