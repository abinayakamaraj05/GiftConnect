document.addEventListener('DOMContentLoaded', () => {
    const tableBody = document.getElementById('categoryTableBody');
    const emptyState = document.getElementById('categoryEmptyState');
    const modal = document.getElementById('categoryModal');
    const modalTitle = document.getElementById('categoryModalTitle');
    const form = document.getElementById('categoryForm');
    const formMessage = document.getElementById('categoryFormMessage');
    const idField = document.getElementById('categoryId');
    const nameField = document.getElementById('categoryName');
    const descField = document.getElementById('categoryDescription');
    const searchInput = document.getElementById('categorySearchInput');

    function openModal(category = null) {
        formMessage.textContent = '';
        formMessage.className = 'form-message';
        if (category) {
            modalTitle.textContent = 'Edit Category';
            idField.value = category.categoryId;
            nameField.value = category.categoryName;
            descField.value = category.description || '';
        } else {
            modalTitle.textContent = 'Add Category';
            form.reset();
            idField.value = '';
        }
        modal.classList.add('open');
    }

    function closeModal() {
        modal.classList.remove('open');
    }

    function renderCategories(categories) {
        tableBody.innerHTML = '';
        emptyState.style.display = categories.length === 0 ? 'block' : 'none';

        categories.forEach(cat => {
            const row = document.createElement('tr');
            row.innerHTML = `
                <td>${cat.categoryId}</td>
                <td>${escapeHtml(cat.categoryName)}</td>
                <td>${escapeHtml(cat.description || '')}</td>
                <td>
                    <button class="btn-small edit" data-action="edit" data-id="${cat.categoryId}">Edit</button>
                    <button class="btn-small delete" data-action="delete" data-id="${cat.categoryId}">Delete</button>
                </td>
            `;
            tableBody.appendChild(row);
        });
    }

    function escapeHtml(str) {
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }

    let currentCategories = [];

    async function loadCategories() {
        try {
            currentCategories = await apiRequest('/api/categories');
            renderCategories(currentCategories);
        } catch (err) {
            alert('Failed to load categories: ' + err.message);
        }
    }

    document.getElementById('openAddCategoryBtn').addEventListener('click', () => openModal());
    document.getElementById('cancelCategoryBtn').addEventListener('click', closeModal);

    // Search
    document.getElementById('categorySearchBtn').addEventListener('click', async () => {
        const keyword = searchInput.value.trim();
        if (!keyword) {
            loadCategories();
            return;
        }
        try {
            const results = await apiRequest('/api/categories/search?name=' + encodeURIComponent(keyword));
            renderCategories(results);
        } catch (err) {
            alert('Search failed: ' + err.message);
        }
    });

    document.getElementById('categoryClearBtn').addEventListener('click', () => {
        searchInput.value = '';
        loadCategories();
    });

    // Edit / Delete (event delegation on the table body)
    tableBody.addEventListener('click', async (e) => {
        const btn = e.target.closest('button');
        if (!btn) return;
        const id = btn.dataset.id;

        if (btn.dataset.action === 'edit') {
            const category = currentCategories.find(c => String(c.categoryId) === id);
            openModal(category);
        }

        if (btn.dataset.action === 'delete') {
            if (!confirm('Delete this category? This will fail if products still use it.')) return;
            try {
                await apiRequest(`/api/categories/${id}`, { method: 'DELETE' });
                loadCategories();
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
            categoryName: nameField.value,
            description: descField.value
        };

        try {
            if (idField.value) {
                await apiRequest(`/api/categories/${idField.value}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
            } else {
                await apiRequest('/api/categories', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
            }
            closeModal();
            loadCategories();
        } catch (err) {
            formMessage.textContent = err.message;
            formMessage.classList.add('error');
        }
    });

    loadCategories();
});
