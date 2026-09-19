document.addEventListener('DOMContentLoaded', async () => {
    const orderTableBody = document.getElementById('orderTableBody');
    const emptyState = document.getElementById('orderEmptyState');

    const newOrderModal = document.getElementById('newOrderModal');
    const newOrderForm = document.getElementById('newOrderForm');
    const newOrderMessage = document.getElementById('newOrderMessage');
    const orderItemsList = document.getElementById('orderItemsList');
    const estimatedTotalEl = document.getElementById('estimatedTotal');
    const shippingAddressField = document.getElementById('shippingAddress');

    const detailModal = document.getElementById('orderDetailModal');
    const detailContent = document.getElementById('orderDetailContent');

    let currentUser = null;
    let products = [];
    let viewingAll = false;

    // ----- Load current user + product catalog -----
    try {
        currentUser = await apiRequest('/api/auth/me');
    } catch (err) {
        return; // auth-guard.js already redirects to index.html on 401
    }

    try {
        products = await apiRequest('/api/products');
    } catch (err) {
        alert('Failed to load products for order form: ' + err.message);
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str ?? '';
        return div.innerHTML;
    }

    // ----- Order list -----
    async function loadOrders() {
        try {
            const url = viewingAll ? '/api/orders' : `/api/orders/user/${currentUser.userId}`;
            const orders = await apiRequest(url);
            renderOrders(orders);
        } catch (err) {
            alert('Failed to load orders: ' + err.message);
        }
    }

    function statusBadge(status) {
        const cls = status === 'CANCELLED' ? 'inactive' : status === 'DELIVERED' ? 'in-stock' : 'out-of-stock';
        return `<span class="badge ${cls}">${status}</span>`;
    }

    function paymentBadge(paymentStatus) {
        const cls = paymentStatus === 'PAID' ? 'in-stock' : paymentStatus === 'FAILED' ? 'out-of-stock' : 'inactive';
        return `<span class="badge ${cls}">${paymentStatus}</span>`;
    }

    function renderOrders(orders) {
        orderTableBody.innerHTML = '';
        emptyState.style.display = orders.length === 0 ? 'block' : 'none';

        orders.forEach(o => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${o.orderId}</td>
                <td>${new Date(o.orderDate).toLocaleString()}</td>
                <td>₹${Number(o.totalAmount).toFixed(2)}</td>
                <td>${statusBadge(o.status)}</td>
                <td>${paymentBadge(o.paymentStatus)}</td>
                <td><button class="btn-small edit" data-action="view" data-id="${o.orderId}">View</button></td>
            `;
            orderTableBody.appendChild(row);
        });
    }

    orderTableBody.addEventListener('click', (e) => {
        const btn = e.target.closest('button[data-action="view"]');
        if (btn) openOrderDetail(btn.dataset.id);
    });

    document.getElementById('viewMyOrdersBtn').addEventListener('click', () => {
        viewingAll = false;
        loadOrders();
    });
    document.getElementById('viewAllOrdersBtn').addEventListener('click', () => {
        viewingAll = true;
        loadOrders();
    });

    // ----- New Order modal -----
    function addItemRow() {
        const row = document.createElement('div');
        row.className = 'order-item-row';
        row.style.cssText = 'display:flex; gap:0.5rem; margin-bottom:0.5rem;';

        const productOptions = products.map(p =>
            `<option value="${p.productId}" data-price="${p.price}">${escapeHtml(p.productName)} (₹${p.price})</option>`
        ).join('');

        row.innerHTML = `
            <select class="item-product" style="flex:2; padding:0.6rem; border:1px solid #ddd; border-radius:6px;">
                ${productOptions}
            </select>
            <input type="number" class="item-qty" value="1" min="1" style="flex:1; padding:0.6rem; border:1px solid #ddd; border-radius:6px;">
            <button type="button" class="btn-small delete remove-item-row">✕</button>
        `;
        orderItemsList.appendChild(row);
        row.querySelector('.item-product').addEventListener('change', recalcTotal);
        row.querySelector('.item-qty').addEventListener('input', recalcTotal);
        row.querySelector('.remove-item-row').addEventListener('click', () => {
            row.remove();
            recalcTotal();
        });
        recalcTotal();
    }

    function recalcTotal() {
        let total = 0;
        orderItemsList.querySelectorAll('.order-item-row').forEach(row => {
            const select = row.querySelector('.item-product');
            const qty = parseInt(row.querySelector('.item-qty').value, 10) || 0;
            const price = parseFloat(select.selectedOptions[0]?.dataset.price || 0);
            total += price * qty;
        });
        estimatedTotalEl.textContent = total.toFixed(2);
    }

    document.getElementById('openNewOrderBtn').addEventListener('click', () => {
        newOrderMessage.textContent = '';
        newOrderMessage.className = 'form-message';
        orderItemsList.innerHTML = '';
        shippingAddressField.value = '';
        addItemRow();
        newOrderModal.classList.add('open');
    });

    document.getElementById('addItemRowBtn').addEventListener('click', addItemRow);
    document.getElementById('cancelNewOrderBtn').addEventListener('click', () => newOrderModal.classList.remove('open'));

    newOrderForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        newOrderMessage.textContent = '';
        newOrderMessage.className = 'form-message';

        const items = Array.from(orderItemsList.querySelectorAll('.order-item-row')).map(row => ({
            productId: parseInt(row.querySelector('.item-product').value, 10),
            quantity: parseInt(row.querySelector('.item-qty').value, 10)
        }));

        if (items.length === 0) {
            newOrderMessage.textContent = 'Add at least one item.';
            newOrderMessage.classList.add('error');
            return;
        }

        try {
            await apiRequest('/api/orders', {
                method: 'POST',
                body: JSON.stringify({
                    userId: currentUser.userId,
                    shippingAddress: shippingAddressField.value,
                    items
                })
            });
            newOrderModal.classList.remove('open');
            loadOrders();
        } catch (err) {
            newOrderMessage.textContent = err.message;
            newOrderMessage.classList.add('error');
        }
    });

    // ----- Order detail / status / cancel / payment -----
    async function openOrderDetail(orderId) {
        try {
            const order = await apiRequest(`/api/orders/${orderId}`);
            renderOrderDetail(order);
            detailModal.classList.add('open');
        } catch (err) {
            alert('Failed to load order: ' + err.message);
        }
    }

    function renderOrderDetail(order) {
        const locked = order.status === 'DELIVERED' || order.status === 'CANCELLED';
        const itemsRows = order.items.map(i => `
            <tr>
                <td>${escapeHtml(i.productName)}</td>
                <td>${i.quantity}</td>
                <td>₹${Number(i.price).toFixed(2)}</td>
                <td>₹${Number(i.subtotal).toFixed(2)}</td>
            </tr>
        `).join('');

        const statusOptions = ['PENDING', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED']
            .map(s => `<option value="${s}" ${s === order.status ? 'selected' : ''}>${s}</option>`).join('');

        const paymentSection = order.paymentStatus === 'PAID'
            ? `<p style="color:#1c7c3c; font-weight:600;">✔ Payment received</p>`
            : locked ? '' : `
                <div style="border-top:1px solid #eee; margin-top:1rem; padding-top:1rem;">
                    <h3 style="font-size:1rem; margin-bottom:0.75rem;">Pay for this order</h3>
                    <select id="paymentMethodSelect" style="padding:0.6rem; border:1px solid #ddd; border-radius:6px; margin-bottom:0.5rem;">
                        <option value="UPI">UPI</option>
                        <option value="CARD">Card</option>
                        <option value="COD">Cash on Delivery</option>
                    </select>
                    <label style="display:block; font-size:0.85rem; margin-bottom:0.5rem;">
                        <input type="checkbox" id="simulateFailureCheckbox"> Simulate a failed payment (for demo)
                    </label>
                    <button class="btn-primary" id="payNowBtn" style="border:none; cursor:pointer;">Pay Now</button>
                    <p class="form-message" id="paymentMessage"></p>
                </div>
            `;

        detailContent.innerHTML = `
            <p><strong>Order #${order.orderId}</strong> — placed ${new Date(order.orderDate).toLocaleString()}</p>
            <p>Shipping to: ${escapeHtml(order.shippingAddress || '—')}</p>
            <table class="data-table" style="margin:1rem 0;">
                <thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead>
                <tbody>${itemsRows}</tbody>
            </table>
            <p><strong>Total: ₹${Number(order.totalAmount).toFixed(2)}</strong></p>

            <div style="display:flex; gap:0.75rem; align-items:center; margin:1rem 0;">
                <label>Status:</label>
                <select id="statusSelect" ${locked ? 'disabled' : ''}
                        style="padding:0.5rem; border:1px solid #ddd; border-radius:6px;">
                    ${statusOptions}
                </select>
                <button class="btn-small edit" id="updateStatusBtn" ${locked ? 'disabled' : ''}>Update</button>
                <button class="btn-small delete" id="cancelOrderBtn" ${locked ? 'disabled' : ''}>Cancel Order</button>
            </div>
            <p class="form-message" id="statusMessage"></p>

            ${paymentSection}
        `;

        const statusMessage = document.getElementById('statusMessage');

        document.getElementById('updateStatusBtn')?.addEventListener('click', async () => {
            const newStatus = document.getElementById('statusSelect').value;
            try {
                const updated = await apiRequest(`/api/orders/${order.orderId}/status`, {
                    method: 'PUT',
                    body: JSON.stringify({ status: newStatus })
                });
                renderOrderDetail(updated);
                loadOrders();
            } catch (err) {
                statusMessage.textContent = err.message;
                statusMessage.classList.add('error');
            }
        });

        document.getElementById('cancelOrderBtn')?.addEventListener('click', async () => {
            if (!confirm('Cancel this order? This will restock its items.')) return;
            try {
                const updated = await apiRequest(`/api/orders/${order.orderId}/cancel`, { method: 'PUT' });
                renderOrderDetail(updated);
                loadOrders();
            } catch (err) {
                statusMessage.textContent = err.message;
                statusMessage.classList.add('error');
            }
        });

        document.getElementById('payNowBtn')?.addEventListener('click', async () => {
            const paymentMessage = document.getElementById('paymentMessage');
            const method = document.getElementById('paymentMethodSelect').value;
            const simulateFailure = document.getElementById('simulateFailureCheckbox').checked;

            try {
                const payment = await apiRequest('/api/payments', {
                    method: 'POST',
                    body: JSON.stringify({ orderId: order.orderId, paymentMethod: method })
                });
                const processed = await apiRequest(
                    `/api/payments/${payment.paymentId}/process?simulateFailure=${simulateFailure}`,
                    { method: 'POST' }
                );

                if (processed.paymentStatus === 'SUCCESS') {
                    const refreshedOrder = await apiRequest(`/api/orders/${order.orderId}`);
                    renderOrderDetail(refreshedOrder);
                    loadOrders();
                } else {
                    paymentMessage.textContent = `Payment failed (ref: ${processed.transactionRef}). Try again.`;
                    paymentMessage.classList.add('error');
                }
            } catch (err) {
                paymentMessage.textContent = err.message;
                paymentMessage.classList.add('error');
            }
        });
    }

    document.getElementById('closeOrderDetailBtn').addEventListener('click', () => {
        detailModal.classList.remove('open');
    });

    loadOrders();
});
