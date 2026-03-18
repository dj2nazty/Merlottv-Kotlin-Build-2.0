const CACHE_NAME = 'merlottv-v4.4';
const CACHE_URLS = [
  './',
  './index.html',
  './manifest.json'
];

// Install: cache the app shell
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(CACHE_URLS))
  );
  self.skipWaiting();
});

// Activate: clean old caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    )
  );
  self.clients.claim();
});

// Fetch: network-first for API calls, cache-first for app shell
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // Always go to network for API calls (Firebase, TMDB, Stremio addons)
  if (url.hostname !== location.hostname) {
    return; // let browser handle normally
  }

  // For app shell files: try network first, fall back to cache (stale-while-revalidate)
  event.respondWith(
    fetch(event.request)
      .then(response => {
        // Clone and cache the fresh response
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        return response;
      })
      .catch(() => {
        // Offline: serve from cache
        return caches.match(event.request).then(cached => {
          return cached || new Response('Offline — please reconnect to load MerlotTV', {
            status: 503,
            headers: { 'Content-Type': 'text/plain' }
          });
        });
      })
  );
});
