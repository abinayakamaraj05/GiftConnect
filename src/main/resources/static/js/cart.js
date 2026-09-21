document.addEventListener('DOMContentLoaded', async () => {
    const content = document.getElementById('cartContent');
    const emptyState = document.getElementById('cartEmpty');
    const tableBody = document.getElementById('cartTableBody');
    const totalEl = document.getElementById('cartTotal');
    const messageEl = document.getElementById('cartMessage');
    const hintEl = document.getElementById('cartHint');
    const checkoutBtn = document.getElementById('checkoutBtn');

    // ----- Who is logged in (auth-guard.js already redirects to login on 401) -----
    let me;
    try {
        me = await apiRequest('/api/auth/me');
    } catch (err) {
        return;
    }
    const cart = CartStore.forUser(me.userId);

    // ----- Current product data (price, stock, status) from the existing API -----
    let productsById = new Map();
    try {
        const products = await apiRequest('/api/products');
        productsById = new Map(products.map(p => [p.productId, p]));
    } catch (err) {
        showMessage('Could not load product details: ' + err.message, true);
        return;
    }

    function showMessage(text, isError) {
        messageEl.textContent = text;
        messageEl.className = 'cart-notice' + (isError ? ' error' : '');
        messageEl.style.display = 'block';
    }

    function clearMessage() {
        messageEl.style.display = 'none';
        messageEl.textContent = '';
    }

    function isUnavailable(product) {
        return !product || product.status !== 'ACTIVE' || product.stock <= 0;
    }

    /** Returns a human-readable problem for this cart line, or null if it is fine. */
    function lineProblem(item, product) {
        if (!product) return 'This product is no longer available.';
        if (product.status !== 'ACTIVE') return 'This product is currently unavailable.';
        if (product.stock <= 0) return 'Out of stock.';
        if (item.quantity > product.stock) return `Only ${product.stock} in stock.`;
        return null;
    }

    // Quantities above current stock are reduced to what is available.
    function reduceToStock() {
        let adjusted = false;
        cart.getItems().forEach(item => {
            const product = productsById.get(item.productId);
            if (!isUnavailable(product) && item.quantity > product.stock) {
                cart.setQuantity(item.productId, product.stock);
                adjusted = true;
            }
        });
        if (adjusted) {
            showMessage('Some quantities were reduced to the stock currently available.', false);
        }
    }

    function render() {
        const items = cart.getItems();
        CartUI.refreshBadge(cart);

        if (items.length === 0) {
            content.style.display = 'none';
            emptyState.style.display = 'block';
            return;
        }
        emptyState.style.display = 'none';
        content.style.display = 'block';

        let totalCents = 0;
        let hasProblem = false;
        tableBody.innerHTML = '';

        items.forEach(item => {
            const product = productsById.get(item.productId);
            const problem = lineProblem(item, product);
            if (problem) hasProblem = true;

            const unavailable = isUnavailable(product);
            const name = product ? product.productName : 'Product #' + item.productId;
            const unitCents = product ? CartUI.toCents(product.price) : 0;
            const lineCents = problem ? 0 : unitCents * item.quantity;
            totalCents += lineCents;

            const thumb = product && product.imageUrl
                ? `<img src="${CartUI.escapeAttr(product.imageUrl)}" alt=""
                        onerror="this.replaceWith(document.createTextNode('🎁'))">`
                : '🎁';
            const maxQty = product ? product.stock : item.quantity;
            const atMax = !product || item.quantity >= product.stock;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td>
                    <div class="cart-line">
                        <div class="cart-thumb">${thumb}</div>
                        <div>
                            <div class="cart-line-name">${CartUI.escapeHtml(name)}</div>
                            ${problem ? `<div class="cart-line-note">${CartUI.escapeHtml(problem)}</div>` : ''}
                        </div>
                    </div>
                </td>
                <td>${product ? CartUI.formatINR(unitCents) : '—'}</td>
                <td>
                    <div class="qty-control">
                        <button type="button" data-action="dec" data-id="${item.productId}"
                                aria-label="Decrease quantity" ${unavailable ? 'disabled' : ''}>−</button>
                        <input type="number" min="1" max="${maxQty}" value="${item.quantity}"
                               data-action="qty" data-id="${item.productId}"
                               aria-label="Quantity" ${unavailable ? 'disabled' : ''}>
                        <button type="button" data-action="inc" data-id="${item.productId}"
                                aria-label="Increase quantity" ${unavailable || atMax ? 'disabled' : ''}>+</button>
                    </div>
                </td>
                <td>${problem ? '—' : CartUI.formatINR(lineCents)}</td>
                <td>
                    <button type="button" class="btn-small delete" data-action="remove"
                            data-id="${item.productId}">Remove</button>
                </td>
            `;
            tableBody.appendChild(row);
        });

        totalEl.textContent = CartUI.formatINR(totalCents);
        checkoutBtn.disabled = hasProblem;
        if (hasProblem) {
            hintEl.textContent = 'Remove the unavailable items or lower their quantity to continue.';
            hintEl.style.display = 'block';
        } else {
            hintEl.style.display = 'none';
        }
    }

    function changeQuantity(productId, requested) {
        const product = productsById.get(productId);
        let quantity = Math.max(1, requested);

        if (product && quantity > product.stock) {
            quantity = product.stock;
            showMessage(`Only ${product.stock} of "${product.productName}" in stock.`, true);
        } else {
            clearMessage();
        }
        cart.setQuantity(productId, quantity);
        render();
    }

    tableBody.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-action]');
        if (!btn) return;
        const productId = Number(btn.dataset.id);

        if (btn.dataset.action === 'remove') {
            cart.remove(productId);
            clearMessage();
            render();
        } else if (btn.dataset.action === 'inc') {
            changeQuantity(productId, cart.getQuantity(productId) + 1);
        } else if (btn.dataset.action === 'dec') {
            changeQuantity(productId, cart.getQuantity(productId) - 1);
        }
    });

    tableBody.addEventListener('change', (e) => {
        const input = e.target.closest('input[data-action="qty"]');
        if (!input) return;
        const productId = Number(input.dataset.id);
        const typed = parseInt(input.value, 10);
        changeQuantity(productId, Number.isNaN(typed) ? cart.getQuantity(productId) : typed);
    });

    checkoutBtn.addEventListener('click', () => {
        window.location.href = 'checkout.html';
    });

    reduceToStock();
    render();
});
