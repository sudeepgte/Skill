/* global self, caches, fetch, Response, URL */
const CACHE_NAME = 'skillhub-v1';
const MEDIA_CACHE_NAME = 'skillhub-media-v1';
const STATIC_ASSETS = [
    '/css/main.css',
    '/js/offline-manager.js',
    '/user/home'
];

self.addEventListener('install', function(event) {
    event.waitUntil(
        caches.open(CACHE_NAME).then(function(cache) {
            console.log('SW: Pre-caching static assets');
            return cache.addAll(STATIC_ASSETS).catch(function(err) {
                console.error('SW: Pre-caching failed', err);
            });
        })
    );
});

self.addEventListener('activate', function(event) {
    event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', function(event) {
    // Only handle GET requests for caching
    if (event.request.method !== 'GET') {
        return;
    }

    const url = new URL(event.request.url);

    // Special handling for video/large assets (Range Request support)
    if (url.pathname.startsWith('/uploads/')) {
        event.respondWith(handleRangeRequest(event));
        return;
    }

    // Default strategy: Cache First for static, Network First for others
    if (STATIC_ASSETS.includes(url.pathname)) {
        event.respondWith(
            caches.match(event.request).then(function(response) {
                return response || fetch(event.request).catch(function() { return null; });
            })
        );
    } else {
        event.respondWith(
            fetch(event.request).catch(function() {
                return caches.match(event.request);
            })
        );
    }
});

/**
 * Handles Range Requests for video/audio to enable seek and playback in desktop/mobile browsers
 */
async function handleRangeRequest(event) {
    try {
        const cache = await caches.open(MEDIA_CACHE_NAME);
        const cachedResponse = await cache.match(event.request);

        if (cachedResponse) {
            const rangeHeader = event.request.headers.get('range');
            if (rangeHeader) {
                const fullBlob = await cachedResponse.blob();
                const parts = rangeHeader.replace(/bytes=/, "").split("-");
                const start = parseInt(parts[0], 10);
                const end = parts[1] ? parseInt(parts[1], 10) : fullBlob.size - 1;
                
                const slicedBlob = fullBlob.slice(start, end + 1);
                return new Response(slicedBlob, {
                    status: 206,
                    statusText: 'Partial Content',
                    headers: {
                        ...cachedResponse.headers,
                        'Content-Range': 'bytes ' + start + '-' + end + '/' + fullBlob.size,
                        'Content-Length': slicedBlob.size,
                        'Accept-Ranges': 'bytes'
                    }
                });
            }
            return cachedResponse;
        }
    } catch (err) {
        console.error("Range request handling failed:", err);
    }

    // Fallback to network if not in cache or error
    return fetch(event.request);
}
