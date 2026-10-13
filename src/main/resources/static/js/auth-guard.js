/**
 * Include this script on every page that requires login, OR on pages that are
 * browsable without one (Phase 2 catalog pages: categories.html, products.html).
 *
 * It checks GET /api/auth/me on load:
 *   - no session, strict mode          -> redirect to the login page immediately
 *   - no session, optional mode        -> stay put and offer a Login link instead
 *   - session present                  -> fill id="currentUserEmail" + wire logout
 *
 * Optional mode is opted into with <script src="js/auth-guard.js" data-optional="true">.
 * Anonymous catalog pages use it so logged-out visitors can still browse.
 */
const GC_OPTIONAL_AUTH = (() => {
    const self = document.currentScript;
    return !!(self && self.dataset && self.dataset.optional === 'true');
})();

document.addEventListener('DOMContentLoaded', () => {
    fetch('/api/auth/me', { credentials: 'include' })
        .then(res => res.ok ? res.json() : Promise.reject())
        .then(user => {
            const emailLabel = document.getElementById('currentUserEmail');
            if (emailLabel) emailLabel.textContent = user.email;
        })
        .catch(() => {
            if (!GC_OPTIONAL_AUTH) {
                window.location.href = 'index.html';
                return;
            }

            // Public page: keep the catalog visible and swap the user area for a
            // login link, since there is no session to log out of.
            const emailLabel = document.getElementById('currentUserEmail');
            if (emailLabel) {
                emailLabel.textContent = '';
                const login = document.createElement('a');
                login.href = 'index.html';
                login.textContent = 'Login / Register';
                emailLabel.appendChild(login);
            }
            const anonymousLogout = document.getElementById('navLogoutBtn');
            if (anonymousLogout) anonymousLogout.style.display = 'none';
        });

    const logoutBtn = document.getElementById('navLogoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
            window.location.href = 'index.html';
        });
    }
});

/**
 * Small fetch wrapper for the Category/Product APIs. Always sends the
 * session cookie and parses JSON. Throws an Error with the server's
 * message on non-2xx responses so callers can just try/catch.
 */
async function apiRequest(url, options = {}) {
    const res = await fetch(url, {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        ...options
    });

    // DELETE requests return 204 No Content — no body to parse
    if (res.status === 204) return null;

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
        // Validation errors come back as {field: message, ...}; others as {error: "..."}
        const message = data.error || Object.values(data)[0] || `Request failed (${res.status})`;
        const error = new Error(message);
        // Expose the HTTP status so callers can show a friendly message for
        // authorization failures (e.g. 403) without surfacing server internals.
        error.status = res.status;
        throw error;
    }

    return data;
}
