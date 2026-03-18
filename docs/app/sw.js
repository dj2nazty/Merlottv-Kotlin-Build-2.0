const CACHE_NAME = 'merlottv-v4.8';
const CACHE_URLS = [
  './',
  './index.html',
  './manifest.json',
  './icon-192.png',
  './icon-512.png',
  './apple-touch-icon.png',
  './favicon-32.png',
  './favicon-16.png'
];

// Listen for skip-waiting message from the page
self.addEventListener('message', event => {
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

// Install: cache the app shell immediately (forces fresh fetch, bypasses old cache)
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => {
      // Force network fetch for all app shell files (don't use stale cache)
      return Promise.all(
        CACHE_URLS.map(url =>
          fetch(url, { cache: 'no-cache' })
            .then(resp => cache.put(url, resp))
            .catch(() => cache.add(url)) // fallback if no-cache fails
        )
      );
    })
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

// Fetch: cache-first for app shell (reliable offline), network-only for API calls
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);

  // External requests (Firebase, TMDB, Stremio, TorBox) — network only
  if (url.hostname !== location.hostname) {
    return; // let browser handle normally
  }

  // Navigation requests (opening the app) — cache-first so it ALWAYS opens offline
  if (event.request.mode === 'navigate') {
    event.respondWith(
      caches.match('./index.html').then(cached => {
        // Always serve from cache first (instant offline), then update cache in background
        const networkFetch = fetch(event.request).then(response => {
          const clone = response.clone();
          caches.open(CACHE_NAME).then(cache => cache.put('./index.html', clone));
          return response;
        }).catch(() => null);

        if (cached) {
          // Serve cached immediately, update in background (stale-while-revalidate)
          networkFetch; // fire and forget
          return cached;
        }
        // No cache yet — must go to network
        return networkFetch.then(resp => resp || new Response(
          '<!DOCTYPE html><html><body style="background:#0D0D0D;color:#fff;font-family:sans-serif;padding:40px;text-align:center"><h2>MerlotTV</h2><p>You are offline. Please connect to the internet to load the app for the first time.</p></body></html>',
          { status: 503, headers: { 'Content-Type': 'text/html' } }
        ));
      })
    );
    return;
  }

  // App shell files (CSS, JS, icons, manifest) — cache-first with background update
  event.respondWith(
    caches.match(event.request).then(cached => {
      const networkFetch = fetch(event.request).then(response => {
        const clone = response.clone();
        caches.open(CACHE_NAME).then(cache => cache.put(event.request, clone));
        return response;
      }).catch(() => null);

      if (cached) {
        networkFetch; // update cache in background
        return cached;
      }
      return networkFetch.then(resp => resp || new Response('Offline', {
        status: 503, headers: { 'Content-Type': 'text/plain' }
      }));
    })
  );
});
