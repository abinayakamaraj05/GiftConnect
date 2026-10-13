/**
 * ADMIN activity dashboard (admin/dashboard.html).
 *
 * Security notes:
 *  - This page is a VIEW only. It calls the GET /api/admin/* endpoints, which
 *    re-check the caller's role server-side on every request — hiding or showing
 *    DOM here is cosmetic and never the access control.
 *  - The role comes from GET /api/auth/me (session backed). Nothing about the
 *    signed-in user is trusted from localStorage, the URL, or a form field.
 *  - Audit rows are display-only: there is no control here that edits or deletes
 *    a log entry, and no such API exists in the backend.
 */

/** Mirrors the backend ActivityType enum so the filter dropdown stays in sync. */
const ACTIVITY_TYPES = [
    'AUTH_LOGIN', 'AUTH_LOGIN_FAILED', 'AUTH_LOGOUT', 'AUTH_REGISTER',
    'AUTH_OTP_SENT', 'AUTH_OTP_VERIFIED', 'AUTH_OTP_FAILED', 'AUTH_PASSWORD_CHANGED',
    'PRODUCT_CREATED', 'PRODUCT_UPDATED', 'PRODUCT_DELETED', 'PRODUCT_VIEWED',
    'CATEGORY_CREATED', 'CATEGORY_UPDATED', 'CATEGORY_DELETED',
    'CART_ITEM_ADDED', 'CART_ITEM_REMOVED', 'CART_UPDATED',
    'ORDER_CREATED', 'ORDER_UPDATED', 'ORDER_CANCELLED', 'ORDER_STATUS_CHANGED',
    'PAYMENT_CREATED', 'PAYMENT_PROCESSED', 'PAYMENT_FAILED',
    'PROFILE_UPDATED',
    'ADMIN_USER_VIEWED', 'ADMIN_USER_UPDATED', 'ADMIN_REPORT_VIEWED'
];

const PAGE_SIZE = 20;
const logState = { page: 0, totalPages: 0 };

document.addEventListener('DOMContentLoaded', init);

async function init() {
    const me = await loadSession();
    if (!me) return; // no session — already redirected

    if (me.role !== 'ADMIN') {
        document.getElementById('adminDenied').style.display = 'block';
        return;
    }

    document.getElementById('adminContent').style.display = 'block';
    populateTypeFilter();
    wireControls();
    await Promise.all([loadOverview(), loadActivity()]);
}

/**
 * Resolves the current session. Redirects to the login page when there is none,
 * and wires the navbar (email label + logout) when there is one.
 */
async function loadSession() {
    let me;
    try {
        const res = await fetch('/api/auth/me', { credentials: 'include' });
        if (!res.ok) throw new Error('not logged in');
        me = await res.json();
    } catch (err) {
        window.location.href = '../index.html';
        return null;
    }

    const emailLabel = document.getElementById('currentUserEmail');
    if (emailLabel) emailLabel.textContent = me.email || '';

    const logoutBtn = document.getElementById('navLogoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', async () => {
            await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
            window.location.href = '../index.html';
        });
    }

    return me;
}

/** GET helper: always sends the session cookie, throws the server's message on error. */
async function apiGet(url) {
    const res = await fetch(url, { credentials: 'include' });
    const data = await res.json().catch(() => ({}));
    if (!res.ok) {
        throw new Error(data.error || `Request failed (${res.status})`);
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

function statusPill(status) {
    const cssClass = status === 'FAILURE' ? 'failure' : 'success';
    return `<span class="status-pill ${cssClass}">${escapeHtml(status)}</span>`;
}

function setMessage(text, isError) {
    const el = document.getElementById('adminMessage');
    el.textContent = text || '';
    el.className = 'form-message' + (text ? (isError ? ' error' : ' success') : '');
}

function populateTypeFilter() {
    const select = document.getElementById('filterType');
    ACTIVITY_TYPES.forEach(type => {
        const option = document.createElement('option');
        option.value = type;
        option.textContent = type;
        select.appendChild(option);
    });
}

function wireControls() {
    document.getElementById('adminRefreshBtn').addEventListener('click', async () => {
        await Promise.all([loadOverview(), loadActivity()]);
    });

    document.getElementById('applyFiltersBtn').addEventListener('click', () => {
        logState.page = 0; // a new filter always starts at the first page
        loadActivity();
    });

    document.getElementById('clearFiltersBtn').addEventListener('click', () => {
        ['filterUserId', 'filterType', 'filterStatus', 'filterEntityType', 'filterFrom', 'filterTo']
            .forEach(id => { document.getElementById(id).value = ''; });
        document.getElementById('filterRole').value = '';
        logState.page = 0;
        loadActivity();
    });

    document.getElementById('prevPageBtn').addEventListener('click', () => {
        if (logState.page > 0) {
            logState.page -= 1;
            loadActivity();
        }
    });

    document.getElementById('nextPageBtn').addEventListener('click', () => {
        if (logState.page + 1 < logState.totalPages) {
            logState.page += 1;
            loadActivity();
        }
    });
}

/** Platform counts plus the two "recent" panels, from ONE request. */
async function loadOverview() {
    try {
        const overview = await apiGet('/api/admin/overview');
        renderOverview(overview);
        renderAuthEvents(overview.recentAuthEvents || []);
        setMessage('', false);
    } catch (err) {
        setMessage(err.message, true);
    }
}

function renderOverview(overview) {
    const tiles = [
        { label: 'Users', value: overview.totalUsers },
        { label: 'Customers', value: overview.totalCustomers },
        { label: 'Sellers', value: overview.totalSellers },
        { label: 'Admins', value: overview.totalAdmins },
        { label: 'Categories', value: overview.totalCategories },
        { label: 'Products', value: overview.totalProducts },
        { label: 'Orders', value: overview.totalOrders },
        { label: 'Payments', value: overview.totalPayments },
        { label: 'Activity log entries', value: overview.totalActivityLogs },
        { label: 'Failed auth events', value: overview.failedAuthEvents, alert: overview.failedAuthEvents > 0 }
    ];

    document.getElementById('overviewGrid').innerHTML = tiles.map(tile => `
        <div class="admin-stat${tile.alert ? ' alert' : ''}">
            <span class="value">${escapeHtml(tile.value)}</span>
            <span class="label">${escapeHtml(tile.label)}</span>
        </div>
    `).join('');
}

function renderAuthEvents(events) {
    const body = document.getElementById('authEventsBody');
    document.getElementById('authEventsEmptyState').style.display = events.length === 0 ? 'block' : 'none';

    body.innerHTML = events.map(event => `
        <tr>
            <td>${escapeHtml(formatWhen(event.createdAt))}</td>
            <td>${escapeHtml(event.email || '—')}</td>
            <td>${escapeHtml(event.userRole || '—')}</td>
            <td>${escapeHtml(event.activityType)}</td>
            <td>${statusPill(event.status)}</td>
            <td>${escapeHtml(event.ipAddress || '—')}</td>
        </tr>
    `).join('');
}

/** Reads the filter controls into a query string; blank controls are omitted. */
function buildFilterQuery() {
    const params = new URLSearchParams();
    const userId = document.getElementById('filterUserId').value.trim();
    const role = document.getElementById('filterRole').value;
    const type = document.getElementById('filterType').value;
    const status = document.getElementById('filterStatus').value;
    const entityType = document.getElementById('filterEntityType').value.trim();
    const from = toIsoOrNull(document.getElementById('filterFrom').value);
    const to = toIsoOrNull(document.getElementById('filterTo').value);

    if (userId) params.set('userId', userId);
    if (role) params.set('role', role);
    if (type) params.set('type', type);
    if (status) params.set('status', status);
    if (entityType) params.set('entityType', entityType);
    if (from) params.set('from', from);
    if (to) params.set('to', to);

    params.set('page', String(logState.page));
    params.set('size', String(PAGE_SIZE));
    return params.toString();
}

/** datetime-local yields "YYYY-MM-DDTHH:mm"; the API expects ISO_DATE_TIME. */
function toIsoOrNull(value) {
    if (!value) return null;
    return value.length === 16 ? value + ':00' : value;
}

async function loadActivity() {
    try {
        const page = await apiGet('/api/admin/activity?' + buildFilterQuery());
        logState.totalPages = page.totalPages;
        renderActivity(page.content || [], page);
        setMessage('', false);
    } catch (err) {
        setMessage(err.message, true);
    }
}

function renderActivity(rows, page) {
    const body = document.getElementById('activityTableBody');
    document.getElementById('activityEmptyState').style.display = rows.length === 0 ? 'block' : 'none';

    body.innerHTML = rows.map(row => `
        <tr>
            <td>${escapeHtml(row.logId)}</td>
            <td>${escapeHtml(formatWhen(row.createdAt))}</td>
            <td>${escapeHtml(row.email || (row.userId != null ? 'user #' + row.userId : 'anonymous'))}</td>
            <td>${escapeHtml(row.activityType)}</td>
            <td>${escapeHtml(row.entityType ? row.entityType + (row.entityId != null ? ' #' + row.entityId : '') : '—')}</td>
            <td>${escapeHtml(row.description || '')}</td>
            <td>${statusPill(row.status)}</td>
            <td>${escapeHtml(row.ipAddress || '—')}</td>
        </tr>
    `).join('');

    const totalPages = Math.max(page.totalPages, 1);
    document.getElementById('pageInfo').textContent =
        `Page ${page.page + 1} of ${totalPages} — ${page.totalElements} entr${page.totalElements === 1 ? 'y' : 'ies'}`;
    document.getElementById('prevPageBtn').disabled = page.page <= 0;
    document.getElementById('nextPageBtn').disabled = page.page + 1 >= totalPages;
}


