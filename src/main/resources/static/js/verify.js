/**
 * Email OTP verification page (verify.html).
 *
 * Security notes:
 *  - The OTP is kept in a page variable only — never in localStorage or a cookie.
 *  - The email being verified travels in the URL fragment (#) on the way in and
 *    is cleared once consumed, so it does not persist in browser history refs.
 */
document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('otpForm');
    const codeInput = document.getElementById('otpCode');
    const messageEl = document.getElementById('otpMessage');
    const introEl = document.getElementById('otpIntro');
    const resendBtn = document.getElementById('resendBtn');
    const resendTimer = document.getElementById('resendTimer');

    function showMessage(text, isError) {
        messageEl.textContent = text;
        messageEl.className = 'form-message' + (isError ? ' error' : ' success');
    }

    // Turn a backend error body into a readable string. Handles both the plain
    // {"error":"..."} shape and the @Valid field-map {"email":"...","otp":"..."}
    // shape, so validation problems are shown instead of a generic message.
    function errorText(data, fallback) {
        if (data && typeof data === 'object') {
            if (typeof data.error === 'string' && data.error) return data.error;
            const parts = Object.keys(data)
                .map(key => data[key])
                .filter(value => typeof value === 'string' && value);
            if (parts.length) return parts.join(' ');
        }
        return fallback;
    }

    // Resolve the email being verified. The fragment is URL-encoded (the caller
    // uses encodeURIComponent), so it must be decoded EXACTLY ONCE before use. It
    // normally holds just the email; it may optionally end with "@" + six digits
    // to pre-fill the code. A malformed fragment must never crash the page.
    // Because the fragment is cleared below, the normalized email is also kept in
    // sessionStorage for this tab so a page reload does not lose it. Only the
    // email is stored — never an OTP, token, or credential.
    let email = '';
    let raw = '';
    const fragment = window.location.hash.replace('#', '');
    if (fragment) {
        try {
            raw = decodeURIComponent(fragment);
        } catch (e) {
            raw = '';
            showMessage('This verification link is malformed. Please go back and register again.', true);
        }
    }
    if (!raw) {
        try {
            raw = sessionStorage.getItem('gcVerifyEmail') || '';
        } catch (e) {
            raw = '';
        }
    }
    if (raw) {
        const m = raw.match(/^(.*@[^@]*)@(\d{6})$/);
        if (m) {
            email = m[1];
            codeInput.value = m[2];
        } else {
            email = raw;
        }
        if (email) {
            try {
                sessionStorage.setItem('gcVerifyEmail', email);
            } catch (e) {
                // sessionStorage may be unavailable (private mode); the page still works.
            }
            if (introEl) introEl.textContent = `Enter the 6-digit code sent to ${email}.`;
        }
        if (fragment) history.replaceState(null, '', window.location.pathname); // clear the fragment
    }

    async function submitOtp(e) {
        e.preventDefault();
        if (!email) {
            showMessage('Missing email — go back and register again.', true);
            return;
        }
        const otp = codeInput.value.trim();
        if (!/^\d{6}$/.test(otp)) {
            showMessage('Enter the 6-digit code.', true);
            return;
        }
        try {
            const res = await fetch('/api/auth/verify-otp', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ email, otp })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                showMessage(errorText(data, 'Verification failed.'), true);
                return;
            }
            showMessage('Email verified! Redirecting to login…', false);
            setTimeout(() => { window.location.href = 'index.html'; }, 1500);
        } catch (err) {
            showMessage('Network error — is the server running?', true);
        }
    }

    let cooldown = 0;
    let timerId = null;
    function startCooldown(seconds) {
        cooldown = seconds;
        resendBtn.disabled = cooldown > 0;
        clearInterval(timerId);
        timerId = setInterval(() => {
            cooldown--;
            resendTimer.textContent = cooldown > 0 ? `wait ${cooldown}s` : '';
            if (cooldown <= 0) {
                resendBtn.disabled = false;
                clearInterval(timerId);
            }
        }, 1000);
    }

    let resending = false;

    resendBtn.addEventListener('click', async () => {
        if (resending) return;                 // a single click sends at most one POST
        if (!email) {
            showMessage('We could not find your email on this page. Please go back and sign in again to verify.', true);
            return;
        }
        if (cooldown > 0) {
            showMessage(`Please wait ${cooldown}s before requesting another code.`, true);
            return;
        }
        resending = true;
        resendBtn.disabled = true;
        try {
            const res = await fetch('/api/auth/resend-otp', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ email })
            });
            const data = await res.json().catch(() => ({}));
            if (!res.ok) {
                showMessage(errorText(data, 'Could not resend the code. Please try again.'), true);
                if (typeof data.resendCooldownSeconds === 'number') startCooldown(data.resendCooldownSeconds);
                return;
            }
            showMessage('A new code has been sent.', false);
            startCooldown(data.resendCooldownSeconds || 60);
        } catch (err) {
            showMessage('Network error — is the server running?', true);
        } finally {
            resending = false;
            // startCooldown() keeps the button disabled during a cooldown;
            // only re-enable here when no cooldown is active.
            if (cooldown <= 0) resendBtn.disabled = false;
        }
    });

    form.addEventListener('submit', submitOtp);
});