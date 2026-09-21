document.addEventListener('DOMContentLoaded', () => {
    const tableBody = document.getElementById('productTableBody');
    const emptyState = document.getElementById('productEmptyState');
    const modal = document.getElementById('productModal');
    const modalTitle = document.getElementById('productModalTitle');
    const form = document.getElementById('productForm');
    const formMessage = document.getElementById('productFormMessage');

    const idField = document.getElementById('productId');
    const nameField = document.getElementById('productName');
    const descField = document.getElementById('productDescription');
    const priceField = document.getElementById('productPrice');
    const stockField = document.getElementById('productStock');
    const categoryField = document.getElementById('productCategory');
    const imageField = document.getElementById('productImageUrl');
    const statusField = document.getElementById('productStatus');

    const searchInput = document.getElementById('productSearchInput');
    const categoryFilterSelect = document.getElementById('categoryFilterSelect');

    // Customer browsing elements (product cards + details modal)
    const cardGrid = document.getElementById('productCardGrid');
    const cardsEmptyState = document.getElementById('productCardsEmptyState');
    const detailModal = document.getElementById('productDetailModal');
    const detailImage = document.getElementById('detailImage');
    const detailPlaceholder = document.getElementById('detailPlaceholder');
    const detailCategory = document.getElementById('detailCategory');
    const detailName = document.getElementById('detailName');
    const detailPrice = document.getElementById('detailPrice');
    const detailDescription = document.getElementById('detailDescription');
    const detailStock = document.getElementById('detailStock');
    const detailAddBtn = document.getElementById('detailAddToCartBtn');

    let currentProducts = [];
    let categories = [];

    // Set in init() once /api/auth/me has answered. Until then the page assumes the
    // least-privileged view (customer), so management controls never flash on screen.
    let isCustomer = true;
    let cart = null;
    let detailProduct = null;

    async function loadCategories() {
        try {
            categories = await apiRequest('/api/categories');

            // Populate the "filter by category" dropdown
            categoryFilterSelect.innerHTML = '<option value="">All categories</option>';
            categories.forEach(cat => {
                const opt = document.createElement('option');
                opt.value = cat.categoryId;
                opt.textContent = cat.categoryName;
                categoryFilterSelect.appendChild(opt);
            });

            // Populate the add/edit form's category dropdown
            categoryField.innerHTML = '<option value="">Select a category</option>';
            categories.forEach(cat => {
                const opt = document.createElement('option');
                opt.value = cat.categoryId;
                opt.textContent = cat.categoryName;
                categoryField.appendChild(opt);
            });
        } catch (err) {
            alert('Failed to load categories: ' + err.message);
        }
    }

    function openModal(product = null) {
        formMessage.textContent = '';
        formMessage.className = 'form-message';

        if (product) {
            modalTitle.textContent = 'Edit Product';
            idField.value = product.productId;
            nameField.value = product.productName;
            descField.value = product.description || '';
            priceField.value = product.price;
            stockField.value = product.stock;
            categoryField.value = product.categoryId;
            imageField.value = product.imageUrl || '';
            statusField.value = product.status;
        } else {
            modalTitle.textContent = 'Add Product';
            form.reset();
            idField.value = '';
        }
        modal.classList.add('open');
    }

    function closeModal() {
        modal.classList.remove('open');
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    // escapeHtml does not escape quotes, so attribute values (src="...", alt="...")
    // need this variant to prevent breaking out of the attribute.
    function escapeAttr(str) {
        return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;').replace(/</g, '&lt;');
    }

    // A product can be added to the cart only if it is ACTIVE and has stock.
    function canPurchase(p) {
        return p.status === 'ACTIVE' && p.inStock;
    }

    function addToCartLabel(p) {
        if (p.status !== 'ACTIVE') return 'Unavailable';
        if (!p.inStock) return 'Out of stock';
        return 'Add to Cart';
    }

    function addToCart(product) {
        if (!cart || !product) return;

        if (!canPurchase(product)) {
            CartUI.showToast('This product is not available right now.', true);
            return;
        }
        const inCart = cart.getQuantity(product.productId);
        if (inCart + 1 > product.stock) {
            CartUI.showToast(`Only ${product.stock} of "${product.productName}" in stock. You already have ${inCart} in your cart.`, true);
            return;
        }
        cart.add(product.productId, 1);
        CartUI.refreshBadge(cart);
        CartUI.showToast(`Added "${product.productName}" to your cart.`);
    }

    // Management-only controls are shown for SELLER / ADMIN, hidden for CUSTOMER.
    // NOTE: this is a display choice only; the real permission checks belong on the server.
    function applyRoleView() {
        document.getElementById('openAddProductBtn').style.display = isCustomer ? 'none' : '';
        document.getElementById('manageSection').style.display = isCustomer ? 'none' : '';
    }

    function renderProducts(products) {
        // Keep the shared list in sync so edit/delete/details always operate on
        // exactly what the user is currently looking at (also after search/filter).
        currentProducts = products;
        tableBody.innerHTML = '';
        emptyState.style.display = products.length === 0 ? 'block' : 'none';
        renderCards(products);

        products.forEach(p => {
            const stockBadge = p.inStock
                ? `<span class="badge in-stock">${p.stock} in stock</span>`
                : `<span class="badge out-of-stock">Out of stock</span>`;

            const statusBadge = p.status === 'ACTIVE'
                ? `<span class="badge in-stock">Active</span>`
                : `<span class="badge inactive">Inactive</span>`;

            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${p.productId}</td>
                <td>${escapeHtml(p.productName)}</td>
                <td>${escapeHtml(p.categoryName)}</td>
                <td>₹${Number(p.price).toFixed(2)}</td>
                <td>${stockBadge}</td>
                <td>${statusBadge}</td>
                <td>
                    <button class="btn-small edit" data-action="edit" data-id="${p.productId}">Edit</button>
                    <button class="btn-small delete" data-action="delete" data-id="${p.productId}">Delete</button>
                </td>
            `;
            tableBody.appendChild(row);
        });
    }

    function renderCards(products) {
        // Customers only see products they can actually buy from; sellers/admins see all.
        const visible = isCustomer ? products.filter(p => p.status === 'ACTIVE') : products;

        cardGrid.innerHTML = '';
        cardsEmptyState.style.display = visible.length === 0 ? 'block' : 'none';

        visible.forEach(p => {
            const stockBadge = p.inStock
                ? `<span class="badge in-stock">${p.stock} in stock</span>`
                : `<span class="badge out-of-stock">Out of stock</span>`;

            const safeImage = p.imageUrl ? escapeAttr(p.imageUrl) : '';
            const imageHtml = safeImage
                ? `<img src="${safeImage}" alt="${escapeAttr(p.productName)}" class="product-card-img"
                       onerror="this.style.display='none'; this.nextElementSibling.style.display='flex';">
                   <div class="product-img-placeholder" style="display:none;">🎁</div>`
                : `<div class="product-img-placeholder">🎁</div>`;

            const card = document.createElement('div');
            card.className = 'product-card';
            card.innerHTML = `
                <div class="product-card-img-wrap">${imageHtml}</div>
                <div class="product-card-body">
                    <span class="product-card-category">${escapeHtml(p.categoryName)}</span>
                    <h3 class="product-card-name">${escapeHtml(p.productName)}</h3>
                    <p class="product-card-desc">${escapeHtml(p.description || 'No description available.')}</p>
                    <div class="product-card-meta">
                        <span class="product-card-price">₹${Number(p.price).toFixed(2)}</span>
                        ${stockBadge}
                    </div>
                    <div class="product-card-actions">
                        <button class="btn-secondary product-details-btn" data-action="details" data-id="${p.productId}">View Details</button>
                        <button class="btn-primary product-details-btn" data-action="add" data-id="${p.productId}"
                                ${canPurchase(p) ? '' : 'disabled'}>${addToCartLabel(p)}</button>
                    </div>
                </div>
            `;
            cardGrid.appendChild(card);
        });
    }

    function updateDetailStock(p) {
        detailStock.innerHTML = p.inStock
            ? `<span class="badge in-stock">${p.stock} in stock</span>`
            : `<span class="badge out-of-stock">Out of stock</span>`;
    }

    function updateDetailAddButton(p) {
        detailAddBtn.disabled = !canPurchase(p);
        detailAddBtn.textContent = addToCartLabel(p);
    }

    function openProductDetails(product) {
        detailProduct = product;
        detailCategory.textContent = product.categoryName || '';
        detailName.textContent = product.productName;
        detailPrice.textContent = '₹' + Number(product.price).toFixed(2);
        detailDescription.textContent = product.description || 'No description available.';

        if (product.imageUrl) {
            detailImage.onerror = () => {
                detailImage.style.display = 'none';
                detailPlaceholder.style.display = 'flex';
            };
            detailImage.src = product.imageUrl;
            detailImage.style.display = 'block';
            detailPlaceholder.style.display = 'none';
        } else {
            detailImage.removeAttribute('src');
            detailImage.style.display = 'none';
            detailPlaceholder.style.display = 'flex';
        }

        updateDetailStock(product);
        updateDetailAddButton(product);
        detailModal.classList.add('open');

        // Refresh stock from the dedicated availability endpoint (existing backend
        // feature) so the modal shows the current value, not just the list snapshot.
        apiRequest(`/api/products/${product.productId}/availability`)
            .then(fresh => {
                if (detailProduct && detailProduct.productId === fresh.productId) {
                    detailProduct = fresh;
                    updateDetailStock(fresh);
                    updateDetailAddButton(fresh);
                }
            })
            .catch(() => { /* keep the stock shown from the list data */ });
    }

    function closeProductDetails() {
        detailModal.classList.remove('open');
        detailProduct = null;
    }

    async function loadProducts() {
        try {
            currentProducts = await apiRequest('/api/products');
            renderProducts(currentProducts);
        } catch (err) {
            alert('Failed to load products: ' + err.message);
        }
    }

    document.getElementById('openAddProductBtn').addEventListener('click', () => openModal());
    document.getElementById('cancelProductBtn').addEventListener('click', closeModal);

    // Search by name
    document.getElementById('productSearchBtn').addEventListener('click', async () => {
        const keyword = searchInput.value.trim();
        if (!keyword) {
            loadProducts();
            return;
        }
        try {
            const results = await apiRequest('/api/products/search?name=' + encodeURIComponent(keyword));
            renderProducts(results);
        } catch (err) {
            alert('Search failed: ' + err.message);
        }
    });

    // Filter by category (shared with the ?category= deep link from categories.html)
    async function applyCategoryFilter(categoryId) {
        if (!categoryId) {
            loadProducts();
            return;
        }
        try {
            const results = await apiRequest(`/api/products/category/${categoryId}`);
            renderProducts(results);
        } catch (err) {
            alert('Filter failed: ' + err.message);
        }
    }

    categoryFilterSelect.addEventListener('change', () => applyCategoryFilter(categoryFilterSelect.value));

    // Pressing Enter in the search box triggers the same search as the button
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') {
            e.preventDefault();
            document.getElementById('productSearchBtn').click();
        }
    });

    document.getElementById('productClearBtn').addEventListener('click', () => {
        searchInput.value = '';
        categoryFilterSelect.value = '';
        loadProducts();
    });

    // Edit / Delete
    tableBody.addEventListener('click', async (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const id = btn.dataset.id;

        if (btn.dataset.action === 'edit') {
            const product = currentProducts.find(p => String(p.productId) === id);
            openModal(product);
        }

        if (btn.dataset.action === 'delete') {
            if (!confirm('Delete this product?')) return;
            try {
                await apiRequest(`/api/products/${id}`, { method: 'DELETE' });
                loadProducts();
            } catch (err) {
                alert('Delete failed: ' + err.message);
            }
        }
    });

    // Customer "View Details" / "Add to Cart" (event delegation on the card grid)
    cardGrid.addEventListener('click', (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const product = currentProducts.find(p => String(p.productId) === btn.dataset.id);
        if (!product) return;

        if (btn.dataset.action === 'details') {
            openProductDetails(product);
        } else if (btn.dataset.action === 'add') {
            addToCart(product);
        }
    });

    // Product details modal buttons
    document.getElementById('closeProductDetailBtn').addEventListener('click', closeProductDetails);
    detailAddBtn.addEventListener('click', () => addToCart(detailProduct));

    // Add / Edit submit
    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        formMessage.textContent = '';
        formMessage.className = 'form-message';

        const payload = {
            productName: nameField.value,
            description: descField.value,
            price: parseFloat(priceField.value),
            stock: parseInt(stockField.value, 10),
            categoryId: parseInt(categoryField.value, 10),
            imageUrl: imageField.value,
            status: statusField.value
        };

        try {
            if (idField.value) {
                await apiRequest(`/api/products/${idField.value}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
            } else {
                await apiRequest('/api/products', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
            }
            closeModal();
            loadProducts();
        } catch (err) {
            formMessage.textContent = err.message;
            formMessage.classList.add('error');
        }
    });

    (async function init() {
        // Who is logged in? (auth-guard.js already redirects to login when there is no session.)
        try {
            const me = await apiRequest('/api/auth/me');
            isCustomer = me.role === 'CUSTOMER';
            cart = CartStore.forUser(me.userId);
            CartUI.refreshBadge(cart);
        } catch (err) {
            return;
        }
        applyRoleView();

        // The categories page can deep-link here with ?category=<id> to show
        // that category's products immediately.
        const presetCategory = new URLSearchParams(window.location.search).get('category');
        await loadCategories();
        const hasPreset = presetCategory !== null
            && Array.from(categoryFilterSelect.options).some(opt => opt.value === presetCategory);
        if (hasPreset) {
            categoryFilterSelect.value = presetCategory;
            await applyCategoryFilter(presetCategory);
        } else {
            await loadProducts();
        }
    })();
});
