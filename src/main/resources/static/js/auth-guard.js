/**
 * Include this script on every page that requires login
 * (dashboard.html, categories.html, products.html).
 *
 * It checks GET /api/auth/me on load. If there's no active session,
 * it redirects to the login page immediately. If there is one, it
 * fills in any element with id="currentUserEmail" and wires up any
 * element with id="navLogoutBtn".
 */
document.addEventListener('DOMContentLoaded', () => {
    fetch('/api/auth/me', { credentials: 'include' })
        .then(res => res.ok ? res.json() : Promise.reject())
        .then(user => {
            const emailLabel = document.getElementById('currentUserEmail');
            if (emailLabel) emailLabel.textContent = user.email;
        })
        .catch(() => {
            window.location.href = 'index.html';
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
        throw new Error(message);
    }

    return data;
}
