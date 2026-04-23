/**
 * SkillHub Offline Manager
 * Manages IndexedDB storage for lectures and background sync for progress.
 */
const DB_NAME = 'SkillHubOffline';
const DB_VERSION = 2;

const offlineManager = {
    db: null,

    init: async function() {
        if (this.db) return Promise.resolve();
        return new Promise((resolve, reject) => {
            const request = indexedDB.open(DB_NAME, DB_VERSION);
            request.onerror = (e) => {
                console.error("IDB Open Error:", e);
                reject(e);
            };
            request.onsuccess = (e) => {
                this.db = e.target.result;
                resolve();
            };
            request.onupgradeneeded = (e) => {
                const db = e.target.result;
                console.log("Upgrading IDB to version:", DB_VERSION);
                if (!db.objectStoreNames.contains('lectures')) {
                    db.createObjectStore('lectures', { keyPath: 'id' });
                }
                if (!db.objectStoreNames.contains('syncQueue')) {
                    db.createObjectStore('syncQueue', { keyPath: 'id', autoIncrement: true });
                }
            };
        });
    },

    _request: async function(storeName, method, ...args) {
        if (!this.db) {
            await this.init();
        }
        return new Promise((resolve, reject) => {
            try {
                const tx = this.db.transaction(storeName, method === 'get' || method === 'getAll' ? 'readonly' : 'readwrite');
                const store = tx.objectStore(storeName);
                const request = store[method](...args);
                request.onsuccess = () => resolve(request.result);
                request.onerror = () => reject(request.error);
            } catch (err) {
                console.error(`Transaction failed for ${storeName}.${method}:`, err);
                reject(err);
            }
        });
    },

    downloadLecture: async function(lesson) {
        console.log("Starting download for:", lesson.title);
        try {
            const lectureData = {
                id: parseInt(lesson.id, 10),
                courseId: parseInt(lesson.courseId, 10),
                title: lesson.title,
                content: lesson.content || "",
                videoUrl: lesson.videoUrl || "",
                downloadedAt: new Date().toISOString()
            };
            
            await this._request('lectures', 'put', lectureData);
            console.log("Metadata saved to IndexedDB");

            if (lesson.videoUrl && (lesson.videoUrl.startsWith('/uploads/') || lesson.videoUrl.startsWith('http'))) {
                try {
                    const cache = await caches.open('skillhub-media-v1');
                    const response = await fetch(lesson.videoUrl, {
                        mode: 'cors',
                        credentials: 'include'
                    });

                    if (response.ok) {
                        await cache.put(lesson.videoUrl, response);
                        console.log("Media cached successfully");
                    }
                } catch (mediaErr) {
                    console.error("Media caching failed:", mediaErr);
                }
            }
            return true;
        } catch (err) {
            console.error("Critical download error:", err);
            throw err;
        }
    },

    isDownloaded: async function(lectureId) {
        try {
            const id = parseInt(lectureId, 10);
            const lecture = await this._request('lectures', 'get', id);
            return !!lecture;
        } catch (e) {
            return false;
        }
    },

    getOfflineLecture: async function(lectureId) {
        const id = parseInt(lectureId, 10);
        return this._request('lectures', 'get', id);
    },

    queueProgressSync: async function(lectureId, isCompleted) {
        const action = {
            lectureId: lectureId,
            isCompleted: isCompleted,
            timestamp: new Date().toISOString(),
            status: 'PENDING'
        };
        await this._request('syncQueue', 'add', action);
        
        if (navigator.onLine) {
            this.syncNow();
        }
    },

    syncNow: async function() {
        if (!navigator.onLine) return;
        try {
            const requests = await this._request('syncQueue', 'getAll');
            if (!requests || requests.length === 0) return;

            for (let i = 0; i < requests.length; i++) {
                const req = requests[i];
                try {
                    const url = req.isCompleted 
                        ? `/api/enrollments/progress/${req.lectureId}/complete`
                        : `/api/enrollments/progress/${req.lectureId}/incomplete`;
                    
                    const csrfMeta = document.querySelector('meta[name="_csrf"]');
                    const csrfToken = csrfMeta ? csrfMeta.content : '';
                    
                    const response = await fetch(url, {
                        method: 'POST',
                        headers: { 'X-CSRF-TOKEN': csrfToken }
                    });

                    if (response.ok) {
                        await this._request('syncQueue', 'delete', req.id);
                    }
                } catch (err) {
                    console.error("Sync failed for request:", req.id, err);
                }
            }
        } catch (err) {
            console.error("Failed to retrieve sync queue:", err);
        }
    }
};

window.addEventListener('online', function() { offlineManager.syncNow(); });
window.offlineManager = offlineManager;
