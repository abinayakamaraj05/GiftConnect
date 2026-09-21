/**
 * Shared helpers for the customer cart and checkout pages.
 *
 * The cart lives in the browser (localStorage), one cart per logged-in user id,
 * so it needs no new database table. It only stores { productId, quantity }.
 * Prices and stock are always read fresh from the existing /api/products
 * endpoints, and the server re-checks everything (stock, price snapshot,
 * total) when the order is actually placed via POST /api/orders.
 *
 * Load this AFTER auth-guard.js and BEFORE the page script.
 */

const CartStore = (() => {
    const KEY_PREFIX = 'giftconnect_cart_';

    function read(key) {
        try {
            const parsed = JSON.parse(localStorage.getItem(key) || '[]');
            if (!Array.isArray(parsed)) return [];
            return parsed.filter(i => i
                && Number.isInteger(i.productId)
                && Number.isInteger(i.quantity)
                && i.quantity > 0);
        } catch (e) {
            return [];
        }
    }

    function write(key, items) {
        try {
            localStorage.setItem(key, JSON.stringify(items));
        } catch (e) {
            // Storage blocked or full: the cart simply will not persist.
        }
    }

    function forUser(userId) {
        const key = KEY_PREFIX + userId;

        function getItems() {
            return read(key);
        }

        function getQuantity(productId) {
            const item = read(key).find(i => i.productId === productId);
            return item ? item.quantity : 0;
        }

        function add(productId, quantity = 1) {
            const items = read(key);
            const existing = items.find(i => i.productId === productId);
            if (existing) {
                existing.quantity += quantity;
            } else {
                items.push({ productId, quantity });
            }
            write(key, items);
        }

        function remove(productId) {
            write(key, read(key).filter(i => i.productId !== productId));
        }

        function setQuantity(productId, quantity) {
            if (quantity <= 0) {
                remove(productId);
                return;
            }
            const items = read(key);
            const existing = items.find(i => i.productId === productId);
            if (existing) {
                existing.quantity = quantity;
                write(key, items);
            }
        }

        function clear() {
            write(key, []);
        }

        function count() {
            return read(key).reduce((sum, i) => sum + i.quantity, 0);
        }

        return { getItems, getQuantity, add, remove, setQuantity, clear, count };
    }

    return { forUser };
})();

const CartUI = (() => {
    let toastTimer = null;

    function escapeHtml(value) {
        const div = document.createElement('div');
        div.textContent = value == null ? '' : String(value);
        return div.innerHTML;
    }

    // escapeHtml does not escape quotes, so attribute values need this variant.
    function escapeAttr(value) {
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/"/g, '&quot;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
    }

    // Money is summed in paise (integers) so totals never show float drift.
    function toCents(amount) {
        return Math.round(Number(amount) * 100);
    }

    function formatINR(cents) {
        return '₹' + (cents / 100).toFixed(2);
    }

    /** Updates the count next to the "Cart" nav link (element id="cartCount"). */
    function refreshBadge(cart) {
        const el = document.getElementById('cartCount');
        if (!el) return;
        const n = cart.count();
        el.textContent = n > 0 ? String(n) : '';
    }

    function showToast(message, isError = false) {
        let el = document.getElementById('cartToast');
        if (!el) {
            el = document.createElement('div');
            el.id = 'cartToast';
            el.className = 'cart-toast';
            el.setAttribute('role', 'status');
            document.body.appendChild(el);
        }
        el.textContent = message;
        el.classList.toggle('error', isError);
        el.classList.add('show');
        clearTimeout(toastTimer);
        toastTimer = setTimeout(() => el.classList.remove('show'), 2500);
    }

    return { escapeHtml, escapeAttr, toCents, formatINR, refreshBadge, showToast };
})();
