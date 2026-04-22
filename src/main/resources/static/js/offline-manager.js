/**
 * SkillHub Offline Manager
 * Manages IndexedDB storage for lectures and background sync for progress.
 */
const DB_NAME = 'SkillHubOffline';
const DB_VERSION = 2; // Bumped to 2 to force schema update

const offlineManager = {
    db: null,

    async init() {
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

    // Wrap IDB operations in Promises
    _request(storeName, method, ...args) {
        return new Promise((resolve, reject) => {
            if (!this.db) {
                console.error("IDB Request failed: DB not init");
                reject(new Error("Database not initialized"));
                return;
            }
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

    async downloadLecture(lesson) {
        console.log("Starting download for:", lesson.title);
        
        try {
            // 1. Cache the Metadata in IndexedDB
            const lectureData = {
                id: parseInt(lesson.id), // Ensure ID is a number
                courseId: parseInt(lesson.courseId),
                title: lesson.title,
                content: lesson.content || "",
                videoUrl: lesson.videoUrl || "",
                downloadedAt: new Date().toISOString()
            };
            
            await this._request('lectures', 'put', lectureData);
            console.log("Metadata saved to IndexedDB");

            // 2. Cache the Media file in Cache API
            if (lesson.videoUrl && (lesson.videoUrl.startsWith('/uploads/') || lesson.videoUrl.startsWith('http'))) {
                try {
                    const cache = await caches.open('skillhub-media-v1');
                    console.log("Fetching media for cache:", lesson.videoUrl);
                    
                    const response = await fetch(lesson.videoUrl, {
                        mode: 'cors',
                        credentials: 'include'
                    });

                    if (response.ok) {
                        await cache.put(lesson.videoUrl, response);
                        console.log("Media cached successfully");
                    } else {
                        console.warn("Media server returned error status:", response.status);
                    }
                } catch (mediaErr) {
                    console.error("Media caching failed (likely CORS or Disk Quota):", mediaErr);
                    // Non-fatal: user can still see text content
                }
            } else {
                console.log("No valid video URL for caching or YouTube video detected.");
            }
            
            return true;
        } catch (err) {
            console.error("Critical download error:", err);
            throw err; // Re-throw critical errors (IDB failures)
        }
    },

    async isDownloaded(lectureId) {
        try {
            const lecture = await this._request('lectures', 'get', lectureId);
            return !!lecture;
        } catch (e) {
            return false;
        }
    },

    async getOfflineLecture(lectureId) {
        return this._request('lectures', 'get', lectureId);
    },

    async queueProgressSync(lectureId, isCompleted) {
        const action = {
            lectureId,
            isCompleted,
            timestamp: new Date().toISOString(),
            status: 'PENDING'
        };
        await this._request('syncQueue', 'add', action);
        
        if (navigator.onLine) {
            this.syncNow();
        }
    },

    async syncNow() {
        if (!navigator.onLine) return;
        
        try {
            const requests = await this._request('syncQueue', 'getAll');
            if (!requests || requests.length === 0) return;

            for (const req of requests) {
                try {
                    const url = req.isCompleted 
                        ? `/api/enrollments/progress/${req.lectureId}/complete`
                        : `/api/enrollments/progress/${req.lectureId}/incomplete`;
                    
                    const response = await fetch(url, {
                        method: 'POST',
                        headers: {
                            'X-CSRF-TOKEN': document.querySelector('meta[name="_csrf"]').content
                        }
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

window.addEventListener('online', () => offlineManager.syncNow());
window.offlineManager = offlineManager;
