/**
 * SkillHub Global Broadcast Pop-up Notification
 * Ensures that admin broadcast messages appear as a prominent pop-up modal
 * to all logged-in users and instructors across the project,
 * and disappears upon clicking the "X" button, appearing ONLY ONCE.
 */
(function () {
    if (window.__skillhubBroadcastInitialized) return;
    window.__skillhubBroadcastInitialized = true;

    let currentBroadcastId = null;
    let isModalOpen = false;

    // Inject Styles for the Broadcast Modal
    function injectStyles() {
        if (document.getElementById('skillhub-broadcast-styles')) return;
        const style = document.createElement('style');
        style.id = 'skillhub-broadcast-styles';
        style.textContent = `
            .skillhub-broadcast-overlay {
                position: fixed;
                inset: 0;
                width: 100vw;
                height: 100vh;
                background: rgba(15, 23, 42, 0.65);
                backdrop-filter: blur(6px);
                -webkit-backdrop-filter: blur(6px);
                z-index: 9999999;
                display: flex;
                align-items: center;
                justify-content: center;
                padding: 1.5rem;
                box-sizing: border-box;
                opacity: 0;
                visibility: hidden;
                transition: opacity 0.28s cubic-bezier(0.16, 1, 0.3, 1), visibility 0.28s;
            }

            .skillhub-broadcast-overlay.active {
                opacity: 1;
                visibility: visible;
            }

            .skillhub-broadcast-dialog {
                background: #ffffff;
                border-radius: 18px;
                width: 100%;
                max-width: 520px;
                box-shadow: 0 25px 60px -15px rgba(0, 0, 0, 0.35), 0 0 0 1px rgba(99, 102, 241, 0.2);
                overflow: hidden;
                transform: translateY(24px) scale(0.96);
                transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1);
                font-family: 'DM Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                color: #1e293b;
                display: flex;
                flex-direction: column;
            }

            .skillhub-broadcast-overlay.active .skillhub-broadcast-dialog {
                transform: translateY(0) scale(1);
            }

            .skillhub-broadcast-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                padding: 1.1rem 1.4rem;
                background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
                color: #ffffff;
            }

            .skillhub-broadcast-badge {
                display: inline-flex;
                align-items: center;
                gap: 8px;
                font-size: 0.8rem;
                font-weight: 700;
                text-transform: uppercase;
                letter-spacing: 0.06em;
                background: rgba(255, 255, 255, 0.22);
                padding: 5px 12px;
                border-radius: 9999px;
            }

            .skillhub-broadcast-badge i {
                font-size: 0.9rem;
            }

            .skillhub-broadcast-close-btn {
                background: rgba(255, 255, 255, 0.2);
                border: none;
                color: #ffffff;
                width: 34px;
                height: 34px;
                border-radius: 50%;
                font-size: 24px;
                line-height: 1;
                display: flex;
                align-items: center;
                justify-content: center;
                cursor: pointer;
                transition: background 0.2s, transform 0.2s;
                padding: 0;
            }

            .skillhub-broadcast-close-btn:hover {
                background: rgba(255, 255, 255, 0.38);
                transform: rotate(90deg);
            }

            .skillhub-broadcast-body {
                padding: 1.6rem 1.6rem 1.2rem;
                max-height: 58vh;
                overflow-y: auto;
            }

            .skillhub-broadcast-title {
                margin: 0 0 0.5rem 0;
                font-size: 1.35rem;
                font-weight: 700;
                color: #0f172a;
                line-height: 1.35;
            }

            .skillhub-broadcast-meta {
                font-size: 0.78rem;
                color: #64748b;
                margin-bottom: 1.1rem;
                display: flex;
                align-items: center;
                gap: 6px;
            }

            .skillhub-broadcast-text {
                font-size: 0.95rem;
                color: #334155;
                line-height: 1.65;
                white-space: pre-wrap;
                word-break: break-word;
                background: #f8fafc;
                border-radius: 12px;
                padding: 1.1rem;
                border: 1px solid #e2e8f0;
            }

            .skillhub-broadcast-footer {
                padding: 1rem 1.6rem 1.3rem;
                display: flex;
                justify-content: flex-end;
                gap: 0.8rem;
                background: #ffffff;
                border-top: 1px solid #f1f5f9;
            }

            .skillhub-broadcast-dismiss-btn {
                background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%);
                color: #ffffff;
                border: none;
                padding: 0.65rem 1.5rem;
                font-size: 0.9rem;
                font-weight: 600;
                border-radius: 8px;
                cursor: pointer;
                display: inline-flex;
                align-items: center;
                gap: 8px;
                box-shadow: 0 4px 14px rgba(99, 102, 241, 0.32);
                transition: transform 0.15s, box-shadow 0.15s;
            }

            .skillhub-broadcast-dismiss-btn:hover {
                box-shadow: 0 6px 18px rgba(99, 102, 241, 0.45);
                transform: translateY(-1px);
            }
        `;
        document.head.appendChild(style);
    }

    // Build or get modal elements
    function ensureModalElements() {
        let overlay = document.getElementById('skillhubBroadcastOverlay');
        if (!overlay) {
            overlay = document.createElement('div');
            overlay.id = 'skillhubBroadcastOverlay';
            overlay.className = 'skillhub-broadcast-overlay';
            overlay.setAttribute('role', 'dialog');
            overlay.setAttribute('aria-modal', 'true');
            overlay.innerHTML = `
                <div class="skillhub-broadcast-dialog" id="skillhubBroadcastDialog">
                    <div class="skillhub-broadcast-header">
                        <div class="skillhub-broadcast-badge">
                            <i class="fas fa-bullhorn"></i>
                            <span>Announcement</span>
                        </div>
                        <button type="button" class="skillhub-broadcast-close-btn" id="skillhubBroadcastClose" aria-label="Close notification" title="Close">&times;</button>
                    </div>
                    <div class="skillhub-broadcast-body">
                        <h2 class="skillhub-broadcast-title" id="skillhubBroadcastTitle"></h2>
                        <div class="skillhub-broadcast-meta">
                            <i class="far fa-clock"></i>
                            <span id="skillhubBroadcastTime">Just now</span>
                        </div>
                        <div class="skillhub-broadcast-text" id="skillhubBroadcastMessage"></div>
                    </div>
                    <div class="skillhub-broadcast-footer">
                        <button type="button" class="skillhub-broadcast-dismiss-btn" id="skillhubBroadcastDismiss">
                            <i class="fas fa-check"></i> Got it
                        </button>
                    </div>
                </div>
            `;
            document.body.appendChild(overlay);

            // Bind click on "X" mark
            const closeBtn = document.getElementById('skillhubBroadcastClose');
            if (closeBtn) {
                closeBtn.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    closeBroadcastModal();
                });
            }

            // Bind click on "Dismiss" button
            const dismissBtn = document.getElementById('skillhubBroadcastDismiss');
            if (dismissBtn) {
                dismissBtn.addEventListener('click', function (e) {
                    e.preventDefault();
                    e.stopPropagation();
                    closeBroadcastModal();
                });
            }

            // Close on clicking backdrop outside dialog
            overlay.addEventListener('click', function (e) {
                if (e.target === overlay) {
                    closeBroadcastModal();
                }
            });

            // Close on Escape key
            document.addEventListener('keydown', function (e) {
                if (e.key === 'Escape' && isModalOpen) {
                    closeBroadcastModal();
                }
            });
        }
        return overlay;
    }

    // Format createdAt timestamp
    function formatTime(createdAtStr) {
        if (!createdAtStr) return 'Just now';
        try {
            const date = new Date(createdAtStr);
            if (isNaN(date.getTime())) return createdAtStr;
            return date.toLocaleDateString(undefined, {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
                hour: '2-digit',
                minute: '2-digit'
            });
        } catch (e) {
            return 'Just now';
        }
    }

    // Display Broadcast Modal
    function showBroadcastModal(data) {
        if (!data || !data.id) return;
        currentBroadcastId = data.id;

        injectStyles();
        const overlay = ensureModalElements();

        const titleEl = document.getElementById('skillhubBroadcastTitle');
        const msgEl = document.getElementById('skillhubBroadcastMessage');
        const timeEl = document.getElementById('skillhubBroadcastTime');

        if (titleEl) titleEl.innerText = data.title || 'Announcement';
        if (msgEl) msgEl.innerText = data.message || '';
        if (timeEl) timeEl.innerText = formatTime(data.createdAt);

        isModalOpen = true;
        // Small delay to allow CSS transitions to trigger
        setTimeout(() => {
            overlay.classList.add('active');
        }, 30);
    }

    // Close Broadcast Modal and permanently dismiss on server
    function closeBroadcastModal() {
        const overlay = document.getElementById('skillhubBroadcastOverlay');
        if (overlay) {
            overlay.classList.remove('active');
        }
        isModalOpen = false;

        if (currentBroadcastId != null) {
            const idToDismiss = currentBroadcastId;
            currentBroadcastId = null;

            // Notify backend that user clicked "X" / dismissed the broadcast
            // This sets isRead = true, so it will NEVER appear again on subsequent logins or reloads
            fetch('/api/broadcast/dismiss?id=' + encodeURIComponent(idToDismiss), {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                credentials: 'same-origin'
            }).catch(function (err) {
                console.error('Failed to dismiss broadcast on server:', err);
            });
        }
    }

    // Query server for any unread broadcast for the currently logged in user
    function checkActiveBroadcast() {
        if (isModalOpen) return;

        fetch('/api/broadcast/active', {
            method: 'GET',
            headers: {
                'Accept': 'application/json'
            },
            credentials: 'same-origin'
        })
        .then(function (response) {
            if (!response.ok) return null;
            return response.json();
        })
        .then(function (data) {
            if (data && data.active && data.id) {
                showBroadcastModal(data);
            }
        })
        .catch(function (err) {
            // Silently ignore network errors / unauthenticated requests
        });
    }

    // Expose global trigger for WebSocket or manual calls
    window.showSkillHubBroadcast = function (data) {
        if (data && (data.type === 'BROADCAST' || !data.type)) {
            showBroadcastModal(data);
        }
    };

    // Initialize check on DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', checkActiveBroadcast);
    } else {
        checkActiveBroadcast();
    }

    // Periodic check every 15 seconds so active users get the broadcast pop-up in real-time
    setInterval(checkActiveBroadcast, 15000);

    // Also re-check when user switches back to this tab
    document.addEventListener('visibilitychange', function () {
        if (!document.hidden) {
            checkActiveBroadcast();
        }
    });

})();
