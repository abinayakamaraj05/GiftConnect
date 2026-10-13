/**
 * Frontend password strength indicator — cosmetic feedback ONLY.
 * The backend (PasswordPolicy.java) always re-validates independently.
 */
document.addEventListener('DOMContentLoaded', () => {
    const input = document.getElementById('registerPassword');
    const bar = document.getElementById('pwStrengthBar');
    const label = document.getElementById('pwStrengthLabel');
    const hints = document.getElementById('pwHints');
    if (!input) return; // not on the register page

    const RULES = [
        { text: 'At least 8 characters', test: v => v.length >= 8 },
        { text: 'One uppercase letter (A–Z)', test: v => /[A-Z]/.test(v) },
        { text: 'One lowercase letter (a–z)', test: v => /[a-z]/.test(v) },
        { text: 'One number (0–9)', test: v => /[0-9]/.test(v) },
        { text: 'One special character (!@#$…)', test: v => /[^A-Za-z0-9]/.test(v) }
    ];

    input.addEventListener('input', () => {
        const value = input.value;
        const passed = RULES.filter(r => r.test(value)).length;

        if (hints) {
            hints.innerHTML = RULES.map(r =>
                `<li class="${r.test(value) ? 'ok' : ''}">${r.text}</li>`).join('');
        }

        let width = 0, color = '#d1d5db', text = '';
        if (value.length === 0) {
            text = '';
        } else if (passed <= 2) {
            width = 33; color = '#dc2626'; text = 'Weak password';
        } else if (passed <= 4) {
            width = 66; color = '#f59e0b'; text = 'Medium password';
        } else {
            width = 100; color = '#16a34a'; text = 'Strong password';
        }

        if (bar) {
            bar.style.width = width + '%';
            bar.style.backgroundColor = color;
        }
        if (label) {
            label.textContent = text;
            label.style.color = color;
        }
    });

    // Block submission client-side too — but the server re-checks regardless.
    const form = document.getElementById('registerForm');
    if (form) {
        form.addEventListener('submit', (e) => {
            const value = input.value;
            if (!RULES.every(r => r.test(value))) {
                e.preventDefault();
                const msg = document.getElementById('registerMessage');
                if (msg) {
                    msg.textContent = 'Password does not meet all requirements below.';
                    msg.className = 'form-message error';
                }
            }
        });
    }
});