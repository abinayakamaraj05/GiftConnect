/**
 * Checkout uses ONLY the existing backend:
 *   POST /api/orders                      -> create the order (server re-checks stock,
 *                                            snapshots prices, reduces stock, computes total)
 *   POST /api/payments                    -> create a PENDING payment for that order
 *   POST /api/payments/{id}/process       -> simulated gateway result
 *   GET  /api/orders/{id}                 -> show the created order
 */
document.addEventListener('DOMContentLoaded', async () => {
    const emptyState = document.getElementById('checkoutEmpty');
    const problemState = document.getElementById('checkoutProblem');
    const loadError = document.getElementById('checkoutLoadError');
    const layout = document.getElementById('checkoutLayout');
    const confirmation = document.getElementById('orderConfirmation');
    const form = document.getElementById('checkoutForm');
    const messageEl = document.getElementById('checkoutMessage');
    const placeOrderBtn = document.getElementById('placeOrderBtn');
    const reviewBody = document.getElementById('reviewTableBody');
    const reviewTotal = document.getElementById('reviewTotal');
    const simulateFailureBox = document.getElementById('simulateFailure');

    // Optional scheduled-delivery (signature feature) controls.
    const scheduleToggle = document.getElementById('scheduleToggle');
    const scheduleFields = document.getElementById('scheduleFields');
    const deliveryDateInput = document.getElementById('deliveryDate');
    const deliverySlotSelect = document.getElementById('deliverySlot');
    const scheduleHint = document.getElementById('scheduleHint');
    const giftMessageInput = document.getElementById('giftMessage');
    let deliveryOptions = null;

    const fields = {
        fullName: document.getElementById('fullName'),
        phone: document.getElementById('phone'),
        addressLine: document.getElementById('addressLine'),
        city: document.getElementById('city'),
        state: document.getElementById('state'),
        pinCode: document.getElementById('pinCode')
    };

    // ----- Who is logged in (auth-guard.js already redirects to login on 401) -----
    let me;
    try {
        me = await apiRequest('/api/auth/me');
    } catch (err) {
        return;
    }
    const cart = CartStore.forUser(me.userId);
    const addressKey = 'giftconnect_address_' + me.userId;
    CartUI.refreshBadge(cart);

    // ----- Current product data -----
    let products;
    try {
        products = await apiRequest('/api/products');
    } catch (err) {
        loadError.textContent = 'Could not load product details: ' + err.message;
        loadError.style.display = 'block';
        return;
    }
    const productsById = new Map(products.map(p => [p.productId, p]));

    const cartItems = cart.getItems();
    if (cartItems.length === 0) {
        emptyState.style.display = 'block';
        return;
    }

    // Every cart line must still be active and in stock in the chosen quantity.
    const lines = [];
    let hasProblem = false;
    cartItems.forEach(item => {
        const product = productsById.get(item.productId);
        if (!product || product.status !== 'ACTIVE' || product.stock < item.quantity) {
            hasProblem = true;
            return;
        }
        lines.push({ product, quantity: item.quantity });
    });
    if (hasProblem) {
        problemState.style.display = 'block';
        return;
    }

    // ----- Review section -----
    let totalCents = 0;
    lines.forEach(({ product, quantity }) => {
        const unitCents = CartUI.toCents(product.price);
        totalCents += unitCents * quantity;
        const row = document.createElement('tr');
        row.innerHTML = `
            <td>${CartUI.escapeHtml(product.productName)}</td>
            <td>${quantity}</td>
            <td>${CartUI.formatINR(unitCents)}</td>
            <td>${CartUI.formatINR(unitCents * quantity)}</td>
        `;
        reviewBody.appendChild(row);
    });
    reviewTotal.textContent = CartUI.formatINR(totalCents);

    // ----- Pre-fill the last delivery address this user used on this browser -----
    try {
        const saved = JSON.parse(localStorage.getItem(addressKey) || 'null');
        if (saved && typeof saved === 'object') {
            Object.keys(fields).forEach(key => {
                if (typeof saved[key] === 'string') fields[key].value = saved[key];
            });
        }
    } catch (e) {
        // ignore a corrupt saved address
    }
    layout.style.display = 'block';

    // ----- Optional gift scheduling (signature feature) -----
    // The picker is a CONVENIENCE only: the server re-validates the seven-calendar-
    // day rule and the slot on submit, so a tampered or stale date is still
    // rejected. If the metadata can't be loaded, the whole section is hidden rather
    // than letting the customer pick a date the server might refuse.
    try {
        deliveryOptions = await apiRequest('/api/delivery/options');
        deliverySlotSelect.innerHTML = deliveryOptions.slots
            .map(s => `<option value="${CartUI.escapeAttr(s.code)}">${CartUI.escapeHtml(s.label)}</option>`)
            .join('');
        if (deliveryDateInput && deliveryOptions.earliestEligibleDate) {
            deliveryDateInput.min = deliveryOptions.earliestEligibleDate;
            scheduleHint.textContent =
                `Earliest available: ${deliveryOptions.earliestEligibleDate} (${deliveryOptions.timezone}); ` +
                `at least ${deliveryOptions.minAdvanceCalendarDays} days' notice required.`;
        }
    } catch (err) {
        const section = scheduleToggle.closest('.checkout-section');
        if (section) section.style.display = 'none';
    }

    scheduleToggle.addEventListener('change', () => {
        const on = scheduleToggle.checked;
        scheduleFields.style.display = on ? 'block' : 'none';
        if (!on) {
            deliveryDateInput.value = '';
            deliverySlotSelect.selectedIndex = 0;
        }
    });

    // ----- Helpers -----
    function readFields() {
        return {
            fullName: fields.fullName.value.trim(),
            phone: fields.phone.value.replace(/[\s-]/g, ''),
            addressLine: fields.addressLine.value.trim(),
            city: fields.city.value.trim(),
            state: fields.state.value.trim(),
            pinCode: fields.pinCode.value.trim()
        };
    }

    function validate(v) {
        if (!v.fullName) return 'Enter the recipient\'s full name.';
        if (!/^\d{10}$/.test(v.phone)) return 'Enter a valid 10-digit phone number.';
        if (!v.addressLine) return 'Enter the delivery address.';
        if (!v.city) return 'Enter the city.';
        if (!v.state) return 'Enter the state.';
        if (!/^\d{6}$/.test(v.pinCode)) return 'Enter a valid 6-digit PIN code.';
        return null;
    }

    // OrderRequest has one shippingAddress string (max 500 chars), so the
    // separate fields are joined into one readable line. Field limits keep it under 500.
    function composeAddress(v) {
        return `${v.fullName} (${v.phone}), ${v.addressLine}, ${v.city}, ${v.state} - ${v.pinCode}`;
    }

    function showError(text) {
        messageEl.textContent = text;
        messageEl.style.display = 'block';
        if (messageEl.scrollIntoView) {
            messageEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }

    function clearError() {
        messageEl.textContent = '';
        messageEl.style.display = 'none';
    }

    function setBusy(busy) {
        placeOrderBtn.disabled = busy;
        placeOrderBtn.textContent = busy ? 'Placing order…' : 'Place order';
    }

    function showConfirmation(order, paymentMethod, paymentError) {
        const paid = order.paymentStatus === 'PAID';
        const itemsRows = order.items.map(i => `
            <tr>
                <td>${CartUI.escapeHtml(i.productName)}</td>
                <td>${i.quantity}</td>
                <td>${CartUI.formatINR(CartUI.toCents(i.price))}</td>
                <td>${CartUI.formatINR(CartUI.toCents(i.subtotal))}</td>
            </tr>
        `).join('');

        const paymentNote = paid
            ? `<p style="color:#1c7c3c; font-weight:600;">✔ Payment received (${CartUI.escapeHtml(paymentMethod)}).</p>`
            : `<p class="cart-notice error">Payment is still pending. ${CartUI.escapeHtml(paymentError || '')}
                   Open <a href="orders.html">My orders</a> and use Pay Now to try again.</p>`;

        const scheduleNote = order.requestedDeliveryDate
            ? `<p>Scheduled delivery: <strong>${CartUI.escapeHtml(order.requestedDeliveryDate)}</strong>
                   ${order.requestedTimeSlot ? '· ' + CartUI.escapeHtml(order.requestedTimeSlot) : ''}
                   ${order.deliveryTimezone ? '(' + CartUI.escapeHtml(order.deliveryTimezone) + ')' : ''}</p>`
            : '';
        const messageNote = order.giftMessage
            ? `<p>Gift message: “${CartUI.escapeHtml(order.giftMessage)}”</p>`
            : '';

        confirmation.innerHTML = `
            <div class="confirmation-box">
                <h2>Order #${order.orderId} placed</h2>
                <p>Placed ${new Date(order.orderDate).toLocaleString()} · Status: ${CartUI.escapeHtml(order.status)}
                   · Payment: ${CartUI.escapeHtml(order.paymentStatus)}</p>
                <p>Delivering to: ${CartUI.escapeHtml(order.shippingAddress || '—')}</p>
                ${scheduleNote}
                ${messageNote}
                <table class="data-table" style="margin:1rem 0;">
                    <thead><tr><th>Product</th><th>Qty</th><th>Price</th><th>Subtotal</th></tr></thead>
                    <tbody>${itemsRows}</tbody>
                </table>
                <p class="review-total">Total: ${CartUI.formatINR(CartUI.toCents(order.totalAmount))}</p>
                ${paymentNote}
                <div class="cart-actions">
                    <a href="orders.html" class="btn-primary link-btn">View my orders</a>
                    <a href="products.html" class="btn-secondary link-btn">Continue shopping</a>
                </div>
            </div>
        `;
        layout.style.display = 'none';
        confirmation.style.display = 'block';
    }

    // ----- Place order -----
    let submitting = false;

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        if (submitting) return;
        clearError();

        const values = readFields();
        const problem = validate(values);
        if (problem) {
            showError(problem);
            return;
        }

        // Scheduled delivery is optional; when chosen, both a date and a slot are
        // required and the date must respect the minimum advance notice. This is a
        // fast client-side check only — DeliveryPolicy enforces the real rule.
        const scheduleBody = {};
        if (scheduleToggle.checked) {
            const dateValue = deliveryDateInput.value;
            const slotValue = deliverySlotSelect.value;
            if (!dateValue) {
                showError('Choose a delivery date, or untick scheduled delivery.');
                return;
            }
            if (!slotValue) {
                showError('Choose a preferred delivery time slot.');
                return;
            }
            if (deliveryOptions && deliveryOptions.earliestEligibleDate
                    && dateValue < deliveryOptions.earliestEligibleDate) {
                showError(`Scheduled delivery needs at least ${deliveryOptions.minAdvanceCalendarDays} ` +
                    `days' notice. Earliest available: ${deliveryOptions.earliestEligibleDate}.`);
                return;
            }
            scheduleBody.deliveryDate = dateValue;
            scheduleBody.deliverySlot = slotValue;
        }
        const giftMessage = giftMessageInput.value.trim();
        if (giftMessage) {
            scheduleBody.giftMessage = giftMessage;
        }
        // The gift recipient is the person the order is addressed to.
        if (values.fullName) {
            scheduleBody.recipientName = values.fullName;
        }

        const selected = form.querySelector('input[name="paymentMethod"]:checked');
        if (!selected) {
            showError('Select a payment method.');
            return;
        }
        const paymentMethod = selected.value;
        const simulateFailure = simulateFailureBox.checked;

        submitting = true;
        setBusy(true);

        // 1) Create the order. If this fails (e.g. stock changed), nothing was
        //    created and the cart is left untouched so the customer can adjust it.
        let order;
        try {
            // No userId is sent: the server binds the order to the authenticated
            // session user, so it cannot be forged from the client.
            order = await apiRequest('/api/orders', {
                method: 'POST',
                body: JSON.stringify({
                    shippingAddress: composeAddress(values),
                    items: lines.map(l => ({
                        productId: l.product.productId,
                        quantity: l.quantity
                    })),
                    ...scheduleBody
                })
            });
        } catch (err) {
            showError(err.message);
            submitting = false;
            setBusy(false);
            return;
        }

        // The order now exists and its stock is reserved, so the cart is done.
        // Clearing it here prevents a second order if the payment step fails.
        cart.clear();
        CartUI.refreshBadge(cart);
        try {
            localStorage.setItem(addressKey, JSON.stringify(values));
        } catch (err) {
            // address memory is a convenience only
        }

        // 2) Pay using the existing simulated payment flow.
        let paymentError = '';
        try {
            const payment = await apiRequest('/api/payments', {
                method: 'POST',
                body: JSON.stringify({ orderId: order.orderId, paymentMethod })
            });
            const processed = await apiRequest(
                `/api/payments/${payment.paymentId}/process?simulateFailure=${simulateFailure}`,
                { method: 'POST' }
            );
            if (processed.paymentStatus !== 'SUCCESS') {
                paymentError = `Payment failed (ref: ${processed.transactionRef}).`;
            }
        } catch (err) {
            paymentError = err.message;
        }

        // 3) Show the order as the server now has it.
        let finalOrder = order;
        try {
            finalOrder = await apiRequest('/api/orders/' + order.orderId);
        } catch (err) {
            // fall back to the order returned at creation
        }
        showConfirmation(finalOrder, paymentMethod, paymentError);
        submitting = false;
    });
});
