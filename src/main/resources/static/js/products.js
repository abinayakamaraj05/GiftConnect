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

    let currentProducts = [];
    let categories = [];

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

    function renderProducts(products) {
        tableBody.innerHTML = '';
        emptyState.style.display = products.length === 0 ? 'block' : 'none';

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

    // Filter by category
    categoryFilterSelect.addEventListener('change', async () => {
        const categoryId = categoryFilterSelect.value;
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
        await loadCategories();
        await loadProducts();
    })();
});
