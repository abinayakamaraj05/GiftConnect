/**
 * My Profile page (profile.html) — self-service profile edit + password change.
 *
 * Security notes:
 *  - Identity always comes from the session: GET /api/users/me and
 *    PUT /api/users/me never receive a user id, so the page can only ever read
 *    and edit the signed-in user's own record.
 *  - Passwords live ONLY in these in-memory variables and the input fields.
 *    They are never written to localStorage, sessionStorage, the URL, or the
 *    console, and they are cleared by form.reset() after a successful change.
 *  - The backend re-validates everything (PasswordPolicy, current-password
 *    check); the checks below exist purely for instant feedback.
 *  - Every request sends credentials: 'include' so the JSESSIONID cookie goes
 *    with it — no token handling, no auth of our own.
 */

/** Mirrors PasswordPolicy.java + js/password.js for instant feedback only. */
const PW_RULES = [
    { text: 'At least 8 characters', test: v => v.length >= 8 },
    { text: 'One uppercase letter (A–Z)', test: v => /[A-Z]/.test(v) },
    { text: 'One lowercase letter (a–z)', test: v => /[a-z]/.test(v) },
    { text: 'One number (0–9)', test: v => /[0-9]/.test(v) },
    { text: 'One special character (!@#$…)', test: v => /[^A-Za-z0-9]/.test(v) }
];

/** Blocks duplicate submits while a request is in flight. */
let busy = false;

document.addEventListener('DOMContentLoaded', () => {
    loadProfile();
    wireProfileForm();
    wirePasswordForm();
    wirePasswordHints();
});

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

function setMessage(id, text, isError) {
    const el = document.getElementById(id);
    if (!el) return;
    el.textContent = text || '';
    el.className = isError ? 'form-message error' : 'form-message success';
}

/** Disables both submit buttons and shows a pending label while saving. */
function setBusy(pending) {
    busy = pending;
    const profileBtn = document.getElementById('profileSubmitBtn');
    const passwordBtn = document.getElementById('passwordSubmitBtn');

    if (profileBtn) {
        profileBtn.disabled = pending;
        profileBtn.textContent = pending ? 'Saving…' : 'Save Changes';
    }
    if (passwordBtn) {
        passwordBtn.disabled = pending;
        passwordBtn.textContent = pending ? 'Changing…' : 'Change Password';
    }
}

function formatSince(value) {
    if (!value) return '—';
    const date = new Date(value);
    return Number.isNaN(date.getTime()) ? '—' : date.toLocaleDateString();
}

// ------------------------------------------------------------------
// Load the caller's own profile
// ------------------------------------------------------------------

async function loadProfile() {
    try {
        // Session identity only — no id in the URL, no client-supplied target.
        const profile = await apiRequest('/api/users/me');

        document.getElementById('profileName').value = profile.name || '';
        // Read-only: email is the login identity (tied to OTP verification).
        document.getElementById('profileEmail').value = profile.email || '';
        document.getElementById('profileRole').value = profile.role || '';
        document.getElementById('profilePhone').value = profile.phone || '';
        document.getElementById('profileAddress').value = profile.address || '';
        document.getElementById('profileRole').title = formatSince(profile.createdAt);
    } catch (err) {
        // 401 = the AuthFilter refused the request (session gone/expired).
        if (err.status === 401) {
            window.location.href = 'index.html';
            return;
        }
        setMessage('profileMessage', err.message || 'Could not load your profile.', true);
    }
}

// ------------------------------------------------------------------
// Save profile details
// ------------------------------------------------------------------

function wireProfileForm() {
    const form = document.getElementById('profileForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (busy) return; // ignore double-clicks

        const name = document.getElementById('profileName').value.trim();
        if (!name) {
            setMessage('profileMessage', 'Name is required.', true);
            return;
        }

        setBusy(true);
        setMessage('profileMessage', '');

        try {
            // Only the three editable fields — email/role/status/password are
            // never sent because the server has no setter for them anyway.
            await apiRequest('/api/users/me', {
                method: 'PUT',
                body: JSON.stringify({
                    name,
                    phone: document.getElementById('profilePhone').value.trim(),
                    address: document.getElementById('profileAddress').value.trim()
                })
            });
            setMessage('profileMessage', 'Profile updated.', false);
        } catch (err) {
            setMessage('profileMessage', err.message || 'Could not save your profile.', true);
        } finally {
            setBusy(false);
        }
    });
}

// ------------------------------------------------------------------
// Change password
// ------------------------------------------------------------------

function wirePasswordForm() {
    const form = document.getElementById('passwordForm');
    if (!form) return;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (busy) return; // ignore double-clicks

        const current = document.getElementById('currentPassword').value;
        const next = document.getElementById('newPassword').value;
        const confirm = document.getElementById('confirmPassword').value;
        const messageEl = 'passwordMessage';

        if (!current) {
            setMessage(messageEl, 'Enter your current password.', true);
            return;
        }
        if (next !== confirm) {
            setMessage(messageEl, 'New passwords do not match.', true);
            return;
        }
        if (next === current) {
            setMessage(messageEl, 'New password must be different from your current password.', true);
            return;
        }
        if (!PW_RULES.every(r => r.test(next))) {
            setMessage(messageEl, 'New password does not meet all requirements below.', true);
            return;
        }

        setBusy(true);
        setMessage(messageEl, '');

        try {
            await apiRequest('/api/auth/change-password', {
                method: 'POST',
                body: JSON.stringify({ currentPassword: current, newPassword: next })
            });

            // Clear the secrets from the DOM immediately on success.
            form.reset();
            renderHints('');
            setMessage(messageEl, 'Password changed successfully.', false);
        } catch (err) {
            // Server messages are safe here: "current password is incorrect",
            // policy violations, etc. — no credential is ever echoed back.
            setMessage(messageEl, err.message || 'Could not change your password.', true);
        } finally {
            setBusy(false);
        }
    });
}

// ------------------------------------------------------------------
// Cosmetic strength indicator (mirrors js/password.js on the register form)
// ------------------------------------------------------------------

function renderHints(value) {
    const hints = document.getElementById('pwHints');
    const bar = document.getElementById('pwStrengthBar');
    const label = document.getElementById('pwStrengthLabel');

    if (hints) {
        hints.innerHTML = PW_RULES.map(r =>
            `<li class="${r.test(value) ? 'ok' : ''}">${r.text}</li>`).join('');
    }

    const passed = PW_RULES.filter(r => r.test(value)).length;
    let width = 0, color = '#d1d5db', text = '';

    if (value.length === 0) {
        text = '';
    } else if (passed <= 2) {
        width = 33; color = '#dc2626'; text = 'Weak password';
    } else if (passed <= 4) {
        width = 66; color = '#f59e0b'; text = 'Medium password';
    } else {
        width = 100; color = '#16a34a'; text = 'Strong password';
    }

    if (bar) {
        bar.style.width = width + '%';
        bar.style.backgroundColor = color;
    }
    if (label) {
        label.textContent = text;
        label.style.color = color;
    }
}

function wirePasswordHints() {
    const input = document.getElementById('newPassword');
    if (!input) return;
    input.addEventListener('input', () => renderHints(input.value));
    renderHints('');
}
