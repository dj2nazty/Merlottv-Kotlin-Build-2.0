const express = require('express');
const fetch = require('node-fetch');

const app = express();
const PORT = 7000;
const TMDB_API_KEY = '5f35ed9740b3aca008e2b9349f5f6393';
const TMDB_BASE = 'https://api.themoviedb.org/3';
const TMDB_IMG = 'https://image.tmdb.org/t/p';

// CORS for Stremio
app.use((req, res, next) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Headers', '*');
    next();
});

// Network definitions
const NETWORKS = {
    'net.nbc':    { id: 6,   name: 'NBC' },
    'net.abc':    { id: 2,   name: 'ABC' },
    'net.cbs':    { id: 16,  name: 'CBS' },
    'net.fox':    { id: 19,  name: 'FOX' },
    'net.cw':     { id: 71,  name: 'The CW' },
    'net.hbo':    { id: 49,  name: 'HBO' },
    'net.showtime': { id: 67, name: 'Showtime' },
    'net.fx':     { id: 88,  name: 'FX' },
    'net.amc':    { id: 174, name: 'AMC' },
    'net.usa':    { id: 30,  name: 'USA Network' },
    'net.bravo':  { id: 74,  name: 'Bravo' },
    'net.hgtv':   { id: 210, name: 'HGTV' },
    'net.history': { id: 65, name: 'History' },
    'net.pbs':    { id: 14,  name: 'PBS' },
};

const MOVIE_GENRES = [
    { id: 28, name: 'Action' }, { id: 12, name: 'Adventure' }, { id: 16, name: 'Animation' },
    { id: 35, name: 'Comedy' }, { id: 80, name: 'Crime' }, { id: 99, name: 'Documentary' },
    { id: 18, name: 'Drama' }, { id: 10751, name: 'Family' }, { id: 14, name: 'Fantasy' },
    { id: 36, name: 'History' }, { id: 27, name: 'Horror' }, { id: 10402, name: 'Music' },
    { id: 9648, name: 'Mystery' }, { id: 10749, name: 'Romance' }, { id: 878, name: 'Sci-Fi' },
    { id: 53, name: 'Thriller' }, { id: 10752, name: 'War' }, { id: 37, name: 'Western' }
];

const TV_GENRES = [
    { id: 10759, name: 'Action & Adventure' }, { id: 16, name: 'Animation' },
    { id: 35, name: 'Comedy' }, { id: 80, name: 'Crime' }, { id: 99, name: 'Documentary' },
    { id: 18, name: 'Drama' }, { id: 10751, name: 'Family' }, { id: 10762, name: 'Kids' },
    { id: 9648, name: 'Mystery' }, { id: 10763, name: 'News' }, { id: 10764, name: 'Reality' },
    { id: 10765, name: 'Sci-Fi & Fantasy' }, { id: 10766, name: 'Soap' },
    { id: 10767, name: 'Talk' }, { id: 10768, name: 'War & Politics' }, { id: 37, name: 'Western' }
];

// Build manifest
const manifest = {
    id: 'com.merlottv.tmdb',
    version: '1.0.0',
    name: 'MerlotTV+',
    description: 'Upcoming, In Theaters, Airing Today, Top Rated, and Network catalogs powered by TMDB',
    logo: 'https://image.tmdb.org/t/p/w500/wwemzKWzjKYJFfCeiB57q3r4Bcm.png',
    types: ['movie', 'series'],
    idPrefixes: ['tt'],
    resources: ['catalog'],
    catalogs: [
        // Movie catalogs
        {
            id: 'merlot.upcoming', type: 'movie', name: 'Upcoming Movies',
            extra: [{ name: 'genre', options: MOVIE_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        {
            id: 'merlot.now_playing', type: 'movie', name: 'In Theaters Now',
            extra: [{ name: 'genre', options: MOVIE_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        {
            id: 'merlot.top_rated_movies', type: 'movie', name: 'Top Rated Movies',
            extra: [{ name: 'genre', options: MOVIE_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        // TV catalogs
        {
            id: 'merlot.airing_today', type: 'series', name: 'Airing Today',
            extra: [{ name: 'genre', options: TV_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        {
            id: 'merlot.on_the_air', type: 'series', name: 'On The Air',
            extra: [{ name: 'genre', options: TV_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        {
            id: 'merlot.top_rated_series', type: 'series', name: 'Top Rated Series',
            extra: [{ name: 'genre', options: TV_GENRES.map(g => g.name) }, { name: 'skip' }]
        },
        // Network catalogs
        ...Object.entries(NETWORKS).map(([catalogId, net]) => ({
            id: catalogId, type: 'series', name: `${net.name}`,
            extra: [{ name: 'skip' }]
        }))
    ]
};

// TMDB endpoint mapping
const CATALOG_MAP = {
    'merlot.upcoming':          { endpoint: '/movie/upcoming', type: 'movie' },
    'merlot.now_playing':       { endpoint: '/movie/now_playing', type: 'movie' },
    'merlot.top_rated_movies':  { endpoint: '/movie/top_rated', type: 'movie' },
    'merlot.airing_today':      { endpoint: '/tv/airing_today', type: 'tv' },
    'merlot.on_the_air':        { endpoint: '/tv/on_the_air', type: 'tv' },
    'merlot.top_rated_series':  { endpoint: '/tv/top_rated', type: 'tv' },
};

// Helper: fetch from TMDB
async function tmdbFetch(path, params = {}) {
    const url = new URL(`${TMDB_BASE}${path}`);
    url.searchParams.set('api_key', TMDB_API_KEY);
    url.searchParams.set('language', 'en-US');
    Object.entries(params).forEach(([k, v]) => url.searchParams.set(k, v));
    const res = await fetch(url.toString());
    if (!res.ok) throw new Error(`TMDB ${res.status}`);
    return res.json();
}

// Helper: find IMDB ID from TMDB ID
async function getImdbId(tmdbId, mediaType) {
    try {
        const data = await tmdbFetch(`/${mediaType}/${tmdbId}/external_ids`);
        return data.imdb_id || null;
    } catch {
        return null;
    }
}

// Helper: convert TMDB results to Stremio metas
async function toStremioMetas(results, mediaType) {
    // Batch fetch IMDB IDs
    const metas = await Promise.all(results.map(async (item) => {
        const isMovie = mediaType === 'movie';
        const title = isMovie ? item.title : item.name;
        const year = isMovie ? item.release_date : item.first_air_date;

        let imdbId = null;
        try {
            imdbId = await getImdbId(item.id, mediaType);
        } catch {}

        if (!imdbId) return null;

        return {
            id: imdbId,
            type: isMovie ? 'movie' : 'series',
            name: title,
            poster: item.poster_path ? `${TMDB_IMG}/w500${item.poster_path}` : null,
            background: item.backdrop_path ? `${TMDB_IMG}/w1280${item.backdrop_path}` : null,
            posterShape: 'poster',
            year: year ? year.substring(0, 4) : undefined,
            description: item.overview || '',
            imdbRating: item.vote_average ? item.vote_average.toFixed(1) : undefined,
        };
    }));

    return metas.filter(m => m !== null);
}

// Parse extra string: "genre=Action&skip=20" -> { genre: "Action", skip: "20" }
function parseExtra(extraStr) {
    const params = {};
    if (!extraStr) return params;
    extraStr.split('&').forEach(part => {
        const [key, ...rest] = part.split('=');
        params[key] = rest.join('=');
    });
    return params;
}

// MANIFEST
app.get('/manifest.json', (req, res) => {
    res.json(manifest);
});

// CATALOG
app.get('/catalog/:type/:id/:extra?.json', async (req, res) => {
    try {
        const { type, id } = req.params;
        const extraStr = req.params.extra;
        const extra = parseExtra(extraStr);
        const page = extra.skip ? Math.floor(parseInt(extra.skip) / 20) + 1 : 1;

        let results = [];
        let mediaType = type === 'series' ? 'tv' : 'movie';

        // Network catalogs
        if (id.startsWith('net.')) {
            const network = NETWORKS[id];
            if (!network) return res.json({ metas: [] });

            const data = await tmdbFetch('/discover/tv', {
                with_networks: network.id.toString(),
                sort_by: 'popularity.desc',
                page: page.toString(),
            });
            results = data.results || [];
            mediaType = 'tv';
        }
        // Standard catalogs
        else if (CATALOG_MAP[id]) {
            const catalog = CATALOG_MAP[id];
            mediaType = catalog.type;

            const params = { page: page.toString() };

            // Genre filter
            if (extra.genre) {
                const genreList = mediaType === 'movie' ? MOVIE_GENRES : TV_GENRES;
                const genre = genreList.find(g => g.name === extra.genre);
                if (genre) {
                    // Use discover endpoint with genre filter
                    const discoverType = mediaType === 'movie' ? 'movie' : 'tv';
                    const sortMap = {
                        'merlot.upcoming': 'popularity.desc',
                        'merlot.now_playing': 'popularity.desc',
                        'merlot.top_rated_movies': 'vote_average.desc',
                        'merlot.airing_today': 'popularity.desc',
                        'merlot.on_the_air': 'popularity.desc',
                        'merlot.top_rated_series': 'vote_average.desc',
                    };
                    const discoverParams = {
                        with_genres: genre.id.toString(),
                        sort_by: sortMap[id] || 'popularity.desc',
                        page: page.toString(),
                    };
                    if (id.includes('top_rated')) {
                        discoverParams['vote_count.gte'] = '200';
                    }
                    const data = await tmdbFetch(`/discover/${discoverType}`, discoverParams);
                    results = data.results || [];
                } else {
                    const data = await tmdbFetch(catalog.endpoint, params);
                    results = data.results || [];
                }
            } else {
                const data = await tmdbFetch(catalog.endpoint, params);
                results = data.results || [];
            }
        } else {
            return res.json({ metas: [] });
        }

        const metas = await toStremioMetas(results, mediaType);
        res.json({ metas });
    } catch (err) {
        console.error('Catalog error:', err.message);
        res.json({ metas: [] });
    }
});

app.listen(PORT, '0.0.0.0', () => {
    console.log(`MerlotTV+ TMDB Addon running at http://localhost:${PORT}/manifest.json`);
    console.log(`Android emulator access: http://10.0.2.2:${PORT}/manifest.json`);
});
