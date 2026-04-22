const CACHE_NAME = 'skillhub-v1';
const MEDIA_CACHE_NAME = 'skillhub-media-v1';
const STATIC_ASSETS = [
    '/css/main.css',
    '/js/offline-manager.js',
    '/user/home',
    '/images/logo-192.png'
];

self.addEventListener('install', event => {
    event.waitUntil(
        caches.open(CACHE_NAME).then(cache => cache.addAll(STATIC_ASSETS))
    );
});

self.addEventListener('activate', event => {
    event.waitUntil(self.clients.claim());
});

self.addEventListener('fetch', event => {
    const url = new URL(event.request.url);

    // Special handling for video/large assets (Range Request support)
    if (url.pathname.startsWith('/uploads/')) {
        event.respondWith(handleRangeRequest(event));
        return;
    }

    // Default strategy: Cache First for static, Network First for others
    if (STATIC_ASSETS.includes(url.pathname)) {
        event.respondWith(
            caches.match(event.request).then(response => response || fetch(event.request))
        );
    } else {
        event.respondWith(
            fetch(event.request).catch(() => caches.match(event.request))
        );
    }
});

/**
 * Handles Range Requests for video/audio to enable seek and playback in desktop/mobile browsers
 */
async function handleRangeRequest(event) {
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
                    'Content-Range': `bytes ${start}-${end}/${fullBlob.size}`,
                    'Content-Length': slicedBlob.size,
                    'Accept-Ranges': 'bytes'
                }
            });
        }
        return cachedResponse;
    }

    // Fallback to network if not in cache
    return fetch(event.request);
}
