/* ============================================================
   CRM Pro — progressive enhancements.
   No framework, no build step. Every feature degrades gracefully.
   ============================================================ */
(function () {
  'use strict';

  var reduceMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ---------- Mobile navigation drawer ---------- */
  var toggle = document.querySelector('[data-nav-toggle]');
  var backdrop = document.querySelector('[data-nav-close]');
  var sidebar = document.getElementById('app-sidebar');
  var mobile = window.matchMedia('(max-width: 900px)');

  function setNav(open) {
    document.body.classList.toggle('nav-open', open);
    if (toggle) toggle.setAttribute('aria-expanded', String(open));
    if (sidebar) sidebar.setAttribute('aria-hidden', String(!open && mobile.matches));
    if (open && sidebar) {
      var first = sidebar.querySelector('.nav-item');
      if (first) first.focus();
    }
  }

  if (toggle) {
    toggle.addEventListener('click', function () {
      setNav(!document.body.classList.contains('nav-open'));
    });
  }
  if (backdrop) backdrop.addEventListener('click', function () { setNav(false); });

  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape' && document.body.classList.contains('nav-open')) {
      setNav(false);
      if (toggle) toggle.focus();
    }
  });

  if (mobile.addEventListener) {
    mobile.addEventListener('change', function () { if (!mobile.matches) setNav(false); });
  }
  if (mobile.matches) setNav(false);

  /* ---------- Accessible error linking on server-side validation ---------- */
  document.querySelectorAll('form').forEach(function (form) {
    var errors = Array.prototype.slice.call(form.querySelectorAll('.field-error'));
    if (!errors.length) return;

    var summary = document.createElement('div');
    summary.className = 'error-summary';
    summary.setAttribute('tabindex', '-1');
    summary.setAttribute('role', 'alert');

    var heading = document.createElement('h2');
    heading.textContent = 'Please review ' + errors.length + (errors.length === 1 ? ' field' : ' fields');
    var list = document.createElement('ul');

    errors.forEach(function (error, index) {
      var group = error.closest('.form-group');
      var field = group ? group.querySelector('.form-control') : null;
      var fieldId = field && field.id ? field.id : '';

      if (field) {
        error.id = error.id || (fieldId + '-error');
        field.setAttribute('aria-invalid', 'true');
        field.setAttribute('aria-describedby', error.id);
      }

      var item = document.createElement('li');
      var link = document.createElement('a');
      link.href = fieldId ? '#' + fieldId : '#';
      var label = field && field.labels && field.labels[0]
        ? field.labels[0].textContent.replace('*', '').trim()
        : 'Field ' + (index + 1);
      link.textContent = label + ': ' + error.textContent.trim();
      item.appendChild(link);
      list.appendChild(item);
    });

    summary.appendChild(heading);
    summary.appendChild(list);
    form.insertBefore(summary, form.firstChild);
    summary.focus({ preventScroll: reduceMotion });
  });

  /* ---------- Password reveal ---------- */
  document.querySelectorAll('[data-password-toggle]').forEach(function (button) {
    button.addEventListener('click', function () {
      var input = document.getElementById(button.getAttribute('data-password-toggle'));
      if (!input) return;
      var reveal = input.type === 'password';
      input.type = reveal ? 'text' : 'password';
      button.setAttribute('aria-pressed', String(reveal));
      button.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
      var use = button.querySelector('use');
      if (use) use.setAttribute('href', reveal ? '#i-eye-off' : '#i-eye');
    });
  });

  /* ---------- Destructive-action confirmation dialog ---------- */
  var dialog = null;
  var pendingForm = null;
  var confirmButton = null;
  var cancelButton = null;

  function ensureDialog() {
    if (dialog) return dialog;
    dialog = document.createElement('dialog');
    dialog.className = 'confirm-dialog';
    dialog.setAttribute('aria-labelledby', 'confirm-title');
    dialog.setAttribute('aria-describedby', 'confirm-text');
    dialog.innerHTML =
      '<h2 id="confirm-title">Confirm action</h2>' +
      '<p id="confirm-text"></p>' +
      '<div class="confirm-actions">' +
      '<button type="button" class="btn btn-secondary" data-confirm-cancel>Cancel</button>' +
      '<button type="button" class="btn btn-danger" data-confirm-ok>Continue</button>' +
      '</div>';
    document.body.appendChild(dialog);
    confirmButton = dialog.querySelector('[data-confirm-ok]');
    cancelButton = dialog.querySelector('[data-confirm-cancel]');
    cancelButton.addEventListener('click', function () { dialog.close(); });
    confirmButton.addEventListener('click', function () {
      var form = pendingForm;
      dialog.close();
      if (!form) return;
      form.dataset.confirmed = 'true';
      if (typeof form.requestSubmit === 'function') form.requestSubmit();
      else form.submit();
    });
    dialog.addEventListener('close', function () { pendingForm = null; });
    return dialog;
  }

  document.querySelectorAll('form[data-confirm]').forEach(function (form) {
    form.addEventListener('submit', function (event) {
      if (form.dataset.confirmed === 'true') return;
      event.preventDefault();
      var box = ensureDialog();
      box.querySelector('#confirm-text').textContent = form.getAttribute('data-confirm');
      pendingForm = form;
      box.showModal();
    });
  });

  /* ---------- Client-side filtering of the rows already on screen ---------- */
  document.querySelectorAll('table[data-client-filter]').forEach(function (table, index) {
    var wrapper = table.closest('.table-responsive');
    if (!wrapper || !table.tBodies.length) return;

    var id = 'row-filter-' + (index + 1);
    var bar = document.createElement('div');
    bar.className = 'table-filter-bar';
    bar.innerHTML =
      '<label class="sr-only" for="' + id + '">Filter rows on this page</label>' +
      '<svg class="icon icon-sm filter-icon" aria-hidden="true"><use href="#i-search"/></svg>' +
      '<input type="search" id="' + id + '" class="form-control filter-input" ' +
      'placeholder="Filter rows on this page…" aria-controls="' + (table.id || id + '-table') + '"/>' +
      '<span class="table-filter-count" role="status" aria-live="polite"></span>';
    if (!table.id) table.id = id + '-table';
    wrapper.parentNode.insertBefore(bar, wrapper);

    var input = bar.querySelector('input');
    var count = bar.querySelector('.table-filter-count');
    var rows = Array.prototype.slice.call(table.tBodies[0].rows)
      .filter(function (row) { return !row.classList.contains('empty-state'); });

    function apply() {
      var query = input.value.trim().toLowerCase();
      var visible = 0;
      rows.forEach(function (row) {
        var match = !query || row.textContent.toLowerCase().indexOf(query) !== -1;
        row.hidden = !match;
        if (match) visible++;
      });
      count.textContent = query ? visible + ' of ' + rows.length + ' rows' : '';
    }

    input.addEventListener('input', apply);
  });
})();
