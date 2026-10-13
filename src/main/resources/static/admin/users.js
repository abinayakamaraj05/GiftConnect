/**
 * ADMIN user management (admin/users.html).
 *
 * Security notes:
 *  - This page is a VIEW over GET/PUT /api/admin/users*. Every request carries
 *    the session cookie (credentials: 'include'), and the backend re-checks the
 *    caller's role server-side on every call. Showing or hiding DOM here is
 *    cosmetic and is NEVER the access control.
 *  - The signed-in user's role and id come from GET /api/auth/me (session
 *    backed). Nothing is trusted from localStorage, the URL, or a form field.
 *  - Only the safe UserResponse fields are ever rendered; passwords and OTP
 *    material have no property to read, so they cannot leak.
 *  - This script does not create, edit, or delete anything except a user's
 *    status/role through the two guarded admin endpoints. The self-change and
 *    last-active-ADMIN rules are enforced by the backend; the UI additionally
 *    disables the acting admin's own controls and surfaces any server refusal.
 *
 * API contract (must match AdminController):
 *   GET /api/admin/users?search=&role=&status=&page=&size=  -> PagedResponse<UserResponse>
 *   GET /api/admin/users/{id}                              -> UserResponse (404 if absent)
 *   PUT /api/admin/users/{id}/status  {"status":"ACTIVE"|"INACTIVE"}
 *   PUT /api/admin/users/{id}/role    {"role":"CUSTOMER"|"SELLER"|"ADMIN"}
 *
 * PagedResponse fields: content, page, size, totalElements, totalPages.
 * UserResponse fields:  userId, name, email, phone, address, role,
 *                       accountStatus, emailVerified, createdAt.
 */

const PAGE_SIZE = 20;

const ROLES = ['CUSTOMER', 'SELLER', 'ADMIN'];

const state = {
    page: 0,
    totalPages: 0,
    totalElements: 0,
    currentUserId: null
};

/**
 * users.html ships the modal with class="modal", but style.css only styles
 * ".modal-overlay" (display:none) and ".modal-overlay.open" (display:flex).
 * Adding the overlay class at parse time reuses the shared styling and keeps
 * the modal hidden on load without touching users.html or style.css. This
 * script tag sits after the modal in the document, so the element exists.
 */
(function primeModal() {
    const modal = document.getElementById('userModal');
    if (modal) {
        modal.classList.add('modal-overlay');
        modal.classList.remove('open');
    }
})();

document.addEventListener('DOMContentLoaded', init);

async function init() {
    const me = await loadSession();
    if (!me) return; // no session — already redirected

    if (me.role !== 'ADMIN' || me.accountStatus === 'INACTIVE') {
        document.getElementById('usersDenied').style.display = 'block';
        document.getElementById('usersContent').style.display = 'none';
        return;
    }

    state.currentUserId = me.userId != null ? Number(me.userId) : null;

    document.getElementById('usersContent').style.display = 'block';
    wireControls();
    await loadUsers();
}

/**
 * Resolves the current session. Redirects to the login page when there is none,
 * and wires the navbar (email label + logout) when there is one.
 */
async function loadSession() {
    let me;
    try {
        me = await apiRequest('/api/auth/me');
    } catch (err) {
        window.location.href = '../index.html';
        return null;
    }

    const emailLabel = document.getElementById('currentUserEmail');
    if (emailLabel) emailLabel.textContent = me.email || '';

    const logoutBtn = document.getElementById('navLogoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            try {
                await apiRequest('/api/auth/logout', { method: 'POST' });
            } catch (err) {
                // Even if the call fails, drop the user back at the login page.
            }
            window.location.href = '../index.html';
        });
    }

    return me;
}

/**
 * fetch wrapper: always sends the session cookie, parses JSON, and throws an
 * Error carrying the backend message (and HTTP status) on a non-2xx response.
 * A 401 means the session expired, so callers can redirect.
 */
async function apiRequest(url, options = {}) {
    const res = await fetch(url, {
        credentials: 'include',
        headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
        ...options
    });

    if (res.status === 204) return null;

    const data = await res.json().catch(() => ({}));

    if (!res.ok) {
        // Validation errors come back as {field: message, ...}; others as {error:"..."}.
        const message = data.error || Object.values(data)[0] || `Request failed (${res.status})`;
        const error = new Error(message);
        error.status = res.status;
        throw error;
    }

    return data;
}

function escapeHtml(value) {
    const div = document.createElement('div');
    div.textContent = value == null ? '' : String(value);
    return div.innerHTML;
}

function formatWhen(value) {
    if (!value) return '';
    const date = new Date(value);
    return isNaN(date.getTime()) ? String(value) : date.toLocaleString();
}

/** Maps a Role to the pill class users.html defines (.role-pill.admin/...). */
function roleClass(role) {
    const normalized = String(role || '').toLowerCase();
    return ROLES.map(r => r.toLowerCase()).includes(normalized) ? normalized : 'customer';
}

/** Maps an AccountStatus to the pill class (.account-pill.active/inactive). */
function statusClass(status) {
    return status === 'ACTIVE' ? 'active' : 'inactive';
}

function setMessage(text, isError) {
    const el = document.getElementById('usersMessage');
    if (!el) return;
    el.textContent = text || '';
    el.className = 'form-message' + (text ? (isError ? ' error' : ' success') : '');
}

function setModalMessage(text, isError) {
    const el = document.getElementById('modalMessage');
    if (!el) return;
    el.textContent = text || '';
    el.className = 'form-message' + (text ? (isError ? ' error' : ' success') : '');
}

/** Redirects on 401; otherwise shows the message where the caller asks. */
function handleError(err, reporter) {
    if (err && err.status === 401) {
        window.location.href = '../index.html';
        return;
    }
    const message = (err && err.message) ? err.message : 'Something went wrong.';
    if (typeof reporter === 'function') reporter(message, true);
}

function wireControls() {
    document.getElementById('usersRefreshBtn').addEventListener('click', loadUsers);

    document.getElementById('userApplyBtn').addEventListener('click', applyFilters);

    document.getElementById('userClearBtn').addEventListener('click', () => {
        document.getElementById('userSearch').value = '';
        document.getElementById('userRoleFilter').value = '';
        document.getElementById('userStatusFilter').value = '';
        applyFilters();
    });

    // Enter in the search box applies the filters.
    document.getElementById('userSearch').addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            applyFilters();
        }
    });

    document.getElementById('usersPrevBtn').addEventListener('click', () => {
        if (state.page > 0) {
            state.page -= 1;
            loadUsers();
        }
    });

    document.getElementById('usersNextBtn').addEventListener('click', () => {
        if (state.page + 1 < state.totalPages) {
            state.page += 1;
            loadUsers();
        }
    });

    document.getElementById('userModalCloseBtn').addEventListener('click', closeModal);

    // Click on the dimmed backdrop (not the box) closes the modal.
    const modal = document.getElementById('userModal');
    if (modal) {
        modal.addEventListener('click', (e) => {
            if (e.target === modal) closeModal();
        });
    }

    // Delegated handler for the per-row "View" buttons.
    document.getElementById('usersTableBody').addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-action="view"]');
        if (btn && btn.dataset.id) {
            openUserModal(Number(btn.dataset.id));
        }
    });
}

/** A new filter set always restarts at the first page. */
function applyFilters() {
    state.page = 0;
    loadUsers();
}

function buildQuery() {
    const params = new URLSearchParams();
    const search = document.getElementById('userSearch').value.trim();
    const role = document.getElementById('userRoleFilter').value;
    const status = document.getElementById('userStatusFilter').value;

    if (search) params.set('search', search);
    if (role) params.set('role', role);
    if (status) params.set('status', status);
    params.set('page', String(state.page));
    params.set('size', String(PAGE_SIZE));

    return params.toString();
}

async function loadUsers() {
    setMessage('Loading…', false);
    try {
        const page = await apiRequest('/api/admin/users?' + buildQuery());
        state.totalPages = page.totalPages;
        state.totalElements = page.totalElements;
        renderUsers(page.content || [], page);
        setMessage('', false);
    } catch (err) {
        document.getElementById('usersEmptyState').style.display = 'none';
        handleError(err, setMessage);
    }
}

function renderUsers(users, page) {
    const body = document.getElementById('usersTableBody');
    document.getElementById('usersEmptyState').style.display = users.length === 0 ? 'block' : 'none';

    body.innerHTML = users.map(user => {
        const isSelf = state.currentUserId != null && Number(user.userId) === state.currentUserId;
        return `
            <tr>
                <td>${escapeHtml(user.userId)}</td>
                <td>${escapeHtml(user.name)}${isSelf ? ' <em>(you)</em>' : ''}</td>
                <td>${escapeHtml(user.email)}</td>
                <td>${escapeHtml(user.phone || '—')}</td>
                <td><span class="role-pill ${roleClass(user.role)}">${escapeHtml(user.role)}</span></td>
                <td><span class="account-pill ${statusClass(user.accountStatus)}">${escapeHtml(user.accountStatus)}</span></td>
                <td>${user.emailVerified
                    ? '<span class="verified-yes">Yes</span>'
                    : '<span class="verified-no">No</span>'}</td>
                <td>${escapeHtml(formatWhen(user.createdAt))}</td>
                <td>
                    <div class="user-actions">
                        <button class="btn-small view" data-action="view" data-id="${escapeHtml(user.userId)}">View</button>
                    </div>
                </td>
            </tr>`;
    }).join('');

    const totalPages = Math.max(page.totalPages, 1);
    document.getElementById('usersPageInfo').textContent =
        `Page ${page.page + 1} of ${totalPages} — ${page.totalElements} user${page.totalElements === 1 ? '' : 's'}`;
    document.getElementById('usersPrevBtn').disabled = page.page <= 0;
    document.getElementById('usersNextBtn').disabled = page.page + 1 >= totalPages;
}

/**
 * Fetches one user's safe profile (the backend audits this as ADMIN_USER_VIEWED)
 * and opens the details modal. Falls back to the visible message on error.
 */
async function openUserModal(id) {
    try {
        const user = await apiRequest('/api/admin/users/' + encodeURIComponent(id));
        renderUserModal(user);
        const modal = document.getElementById('userModal');
        if (modal) modal.classList.add('open');
    } catch (err) {
        handleError(err, setMessage);
    }
}

function renderUserModal(user) {
    const isSelf = state.currentUserId != null && Number(user.userId) === state.currentUserId;

    const roleOptions = ROLES.map(role =>
        `<option value="${role}"${role === user.role ? ' selected' : ''}>${role}</option>`
    ).join('');

    const statusButtonLabel = user.accountStatus === 'ACTIVE' ? 'Deactivate' : 'Activate';
    const statusButtonClass = user.accountStatus === 'ACTIVE' ? 'delete' : 'edit';

    const controls = isSelf
        ? `<p style="color: var(--text-light); font-size: 0.85rem; margin: 0;">
               You cannot change your own role or deactivate your own account.
           </p>`
        : `<div class="user-actions">
               <select id="modalRoleSelect" title="New role">${roleOptions}</select>
               <button class="btn-small edit" id="modalApplyRoleBtn">Change role</button>
               <button class="btn-small ${statusButtonClass}" id="modalStatusBtn"
                       data-current="${escapeHtml(user.accountStatus)}">${statusButtonLabel}</button>
           </div>
           <p class="form-message" id="modalMessage"></p>`;

    document.getElementById('userModalTitle').textContent = `User #${user.userId}`;
    document.getElementById('userModalBody').innerHTML = `
        <dt>Name</dt><dd>${escapeHtml(user.name)}</dd>
        <dt>Email</dt><dd>${escapeHtml(user.email)}</dd>
        <dt>Phone</dt><dd>${escapeHtml(user.phone || '—')}</dd>
        <dt>Address</dt><dd>${escapeHtml(user.address || '—')}</dd>
        <dt>Role</dt><dd>${escapeHtml(user.role)}</dd>
        <dt>Account status</dt><dd>${escapeHtml(user.accountStatus)}</dd>
        <dt>Email verified</dt><dd>${user.emailVerified ? 'Yes' : 'No'}</dd>
        <dt>Created</dt><dd>${escapeHtml(formatWhen(user.createdAt))}</dd>
        <dt>Admin actions</dt><dd>${controls}</dd>`;

    if (isSelf) return;

    const applyRoleBtn = document.getElementById('modalApplyRoleBtn');
    if (applyRoleBtn) {
        applyRoleBtn.addEventListener('click', () => applyRole(user.userId));
    }

    const statusBtn = document.getElementById('modalStatusBtn');
    if (statusBtn) {
        statusBtn.addEventListener('click', () => applyStatus(user.userId, statusBtn.dataset.current));
    }
}

function closeModal() {
    const modal = document.getElementById('userModal');
    if (modal) modal.classList.remove('open');
}

/** PUT /api/admin/users/{id}/role — backend blocks self-change + last active ADMIN. */
async function applyRole(userId) {
    const select = document.getElementById('modalRoleSelect');
    if (!select) return;

    const role = select.value;
    setModalMessage('', false);

    if (!window.confirm(`Change user #${userId}'s role to ${role}?`)) return;

    try {
        await apiRequest('/api/admin/users/' + encodeURIComponent(userId) + '/role', {
            method: 'PUT',
            body: JSON.stringify({ role: role })
        });
        await loadUsers();
        await openUserModal(userId);
        setModalMessage('Role updated successfully.', false);
    } catch (err) {
        handleError(err, setModalMessage);
    }
}

/** PUT /api/admin/users/{id}/status — backend blocks self-deactivation + last active ADMIN. */
async function applyStatus(userId, currentStatus) {
    const newStatus = currentStatus === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    const verb = newStatus === 'ACTIVE' ? 'Activate' : 'Deactivate';

    setModalMessage('', false);

    if (!window.confirm(`${verb} user #${userId}?`)) return;

    try {
        await apiRequest('/api/admin/users/' + encodeURIComponent(userId) + '/status', {
            method: 'PUT',
            body: JSON.stringify({ status: newStatus })
        });
        await loadUsers();
        await openUserModal(userId);
        setModalMessage(`Account ${verb.toLowerCase()}d successfully.`, false);
    } catch (err) {
        handleError(err, setModalMessage);
    }
}
