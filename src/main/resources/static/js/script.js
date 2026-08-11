// Toggles the mobile navigation menu when the hamburger icon is clicked.
document.addEventListener('DOMContentLoaded', () => {
    const hamburger = document.getElementById('hamburger');
    const navLinks = document.getElementById('navLinks');

    if (hamburger && navLinks) {
        hamburger.addEventListener('click', () => {
            navLinks.classList.toggle('active');
        });
    }

    initAuth();
});

/**
 * Wires up the Login / Register / Logout forms and checks whether a
 * session is already active on page load (GET /api/auth/me).
 * All requests use credentials: 'include' so the JSESSIONID cookie is sent.
 */
function initAuth() {
    const sessionCard = document.getElementById('sessionCard');
    const authForms = document.getElementById('authForms');
    const sessionInfo = document.getElementById('sessionInfo');
    const logoutBtn = document.getElementById('logoutBtn');
    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const loginMessage = document.getElementById('loginMessage');
    const registerMessage = document.getElementById('registerMessage');

    if (!sessionCard || !authForms) return; // auth section not on this page

    function showLoggedInState(user) {
        sessionInfo.textContent = `Logged in as ${user.email}`;
        sessionCard.style.display = 'block';
        authForms.style.display = 'none';
    }

    function showLoggedOutState() {
        sessionCard.style.display = 'none';
        authForms.style.display = 'grid';
    }

    // Check current session on page load
    fetch('/api/auth/me', { credentials: 'include' })
        .then(res => res.ok ? res.json() : Promise.reject())
        .then(showLoggedInState)
        .catch(showLoggedOutState);

    // Login
    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        loginMessage.textContent = '';
        loginMessage.className = 'form-message';

        const email = document.getElementById('loginEmail').value;
        const password = document.getElementById('loginPassword').value;

        try {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ email, password })
            });
            const data = await res.json();

            if (!res.ok) {
                loginMessage.textContent = data.error || 'Login failed';
                loginMessage.classList.add('error');
                return;
            }

            loginForm.reset();
            showLoggedInState({ email: data.email });
        } catch (err) {
            loginMessage.textContent = 'Network error — is the server running?';
            loginMessage.classList.add('error');
        }
    });

    // Register
    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        registerMessage.textContent = '';
        registerMessage.className = 'form-message';

        const payload = {
            name: document.getElementById('registerName').value,
            email: document.getElementById('registerEmail').value,
            password: document.getElementById('registerPassword').value,
            phone: document.getElementById('registerPhone').value,
            address: document.getElementById('registerAddress').value
        };

        try {
            const res = await fetch('/api/auth/register', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify(payload)
            });
            const data = await res.json();

            if (!res.ok) {
                registerMessage.textContent = data.error || 'Registration failed';
                registerMessage.classList.add('error');
                return;
            }

            registerMessage.textContent = 'Account created — you can log in now.';
            registerMessage.classList.add('success');
            registerForm.reset();
        } catch (err) {
            registerMessage.textContent = 'Network error — is the server running?';
            registerMessage.classList.add('error');
        }
    });

    // Logout
    logoutBtn.addEventListener('click', async () => {
        await fetch('/api/auth/logout', { method: 'POST', credentials: 'include' });
        showLoggedOutState();
    });
}
