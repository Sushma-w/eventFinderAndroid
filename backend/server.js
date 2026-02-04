const express = require('express');
const axios = require('axios');
const cors = require('cors');
const { MongoClient } = require('mongodb');
const dotenv = require('dotenv');
dotenv.config();
console.log('MONGO_URI:', process.env.MONGO_URI);
const client = new MongoClient(process.env.MONGO_URI);

// async function connectDB() {
//   try {
//     await client.connect();
//     console.log('✅ MongoDB connected successfully');
//   } catch (err) {
//     console.error('❌ MongoDB connection failed:', err.message);
//   }
// }

// connectDB();
// ============================================
// ✅ MongoDB Connection + Server Startup
// ============================================
async function connectDB(retries = 5) {
  while (retries) {
    try {
      await client.connect();
      console.log('✅ MongoDB connected successfully');
      break; // connected → break out of loop
    } catch (err) {
      retries -= 1;
      console.error(`❌ MongoDB connection failed (${5 - retries}/5):`, err.message);
      if (retries === 0) throw err;
      console.log('⏳ Retrying in 5 seconds...');
      await new Promise(res => setTimeout(res, 5000));
    }
  }
}

// ✅ Only start Express after DB is connected
connectDB()
  .then(() => {
    // app.listen(PORT, '0.0.0.0', () => {
       app.listen(PORT, () => {
      console.log(`Server running on http://localhost:${PORT}`);
    });
  })
  .catch(err => {
    console.error('🚨 Could not connect to MongoDB. Exiting...', err.message);
    process.exit(1);
  });


const app = express();
const PORT = process.env.PORT || 8080;
const API_KEY = process.env.TICKETMASTER_API_KEY;
const GEOCODING_API_KEY = process.env.GOOGLE_GEOCODING_KEY;
const IPINFO_TOKEN = process.env.IPINFO_TOKEN;


app.use(cors());
app.use(express.json()); // ✅ Enable JSON body parsing globally


// ======================================================
// 🧭 Auto-detect location using ipinfo.io
// ======================================================
app.get('/api/autodetect', async (req, res) => {
  try {
    const response = await axios.get(`https://ipinfo.io/json?token=${IPINFO_TOKEN}`);

    // The "loc" field contains "lat,lon"
    const [lat, lon] = response.data.loc.split(',');

    res.json({
      lat,
      lon,
      city: response.data.city,
      region: response.data.region,
      country: response.data.country,
    });
  } catch (error) {
    console.error('❌ Error detecting location:', error.message);
    res.status(500).json({ error: 'Failed to detect location' });
  }
});
// =======================================
// ❤️ Favorites Endpoints (MongoDB CRUD)
// =======================================

// Get all favorites
app.get('/api/favorites', async (req, res) => {
  try {
    const db = client.db(process.env.DB_NAME);
    const favorites = await db.collection(process.env.COLLECTION).find().toArray();
    res.json(favorites);
  } catch (err) {
    console.error('❌ Error fetching favorites:', err.message);
    res.status(500).json({ error: 'Failed to fetch favorites' });
  }
});

// Add to favorites
// ✅ Add to favorites (with full details)
app.post('/api/favorites', async (req, res) => {
  try {
    console.log('🟢 Received favorite:', req.body.name); // <— add this
    const db = client.db(process.env.DB_NAME);
    const favorite = req.body;

    if (!favorite || !favorite.id) {
      return res.status(400).json({ error: 'Invalid event data' });
    }

    // Check for duplicates
    const existing = await db.collection(process.env.COLLECTION).findOne({ id: favorite.id });
    if (existing) {
      return res.status(200).json({ message: 'Already in favorites' });
    }

    // Create the document shape
    const doc = {
      id: favorite.id,
      name: favorite.name,
      venue: favorite._embedded?.venues?.[0]?.name || favorite.venue || 'Unknown Venue',
      image: favorite.images?.[0]?.url || '',
      date: favorite.dates?.start?.localDate || '',
      time: favorite.dates?.start?.localTime || '',
      segment: favorite.classifications?.[0]?.segment?.name || '',
      addedAt: new Date(),
    };

    await db.collection(process.env.COLLECTION).insertOne(doc);
    console.log('✅ Added to favorites:', doc.name);
    res.json({ message: 'Event added to favorites!' });
  } catch (err) {
    console.error('❌ Error adding favorite:', err.message);
    res.status(500).json({ error: 'Failed to add favorite' });
  }
});


// Remove from favorites
app.delete('/api/favorites/:id', async (req, res) => {
  try {
    const db = client.db(process.env.DB_NAME);
    const id = req.params.id;

    await db.collection(process.env.COLLECTION).deleteOne({ id });
    res.json({ message: 'Event removed from favorites!' });
  } catch (err) {
    console.error('❌ Error removing favorite:', err.message);
    res.status(500).json({ error: 'Failed to remove favorite' });
  }
});


// ✅ Ticketmaster search endpoint with Google Maps geocoding
app.get('/api/events', async (req, res) => {
  try {
    const { keyword, segmentId, radius, unit, lat, lon, location } = req.query;

    let latitude = lat;
    let longitude = lon;

    // 🗺️ If manual location provided, geocode it
    if (location && (!lat || !lon)) {
      const geocodeUrl = `https://maps.googleapis.com/maps/api/geocode/json?address=${encodeURIComponent(
  location
)}&key=${GEOCODING_API_KEY}`;


      const geoRes = await axios.get(geocodeUrl);

      if (
        geoRes.data.status === 'OK' &&
        geoRes.data.results &&
        geoRes.data.results[0]
      ) {
        latitude = geoRes.data.results[0].geometry.location.lat;
        longitude = geoRes.data.results[0].geometry.location.lng;
      } else {
        return res.status(400).json({ error: 'Invalid location input' });
      }
    }

    // ✅ Prepare Ticketmaster API parameters
    const params = {
      apikey: API_KEY,
      keyword,
      segmentId,
      radius,
      unit,
      latlong: `${latitude},${longitude}`,
    };

    const tmResponse = await axios.get(
      'https://app.ticketmaster.com/discovery/v2/events',
      { params }
    );

    const allEvents = tmResponse.data._embedded?.events || [];

// Sort by ascending localDate/time
allEvents.sort((a, b) => {
  const dateA = new Date(`${a.dates?.start?.localDate}T${a.dates?.start?.localTime || '00:00:00'}`);
  const dateB = new Date(`${b.dates?.start?.localDate}T${b.dates?.start?.localTime || '00:00:00'}`);
  return dateA.getTime() - dateB.getTime();
});

// Limit to first 20 results
const topEvents = allEvents.slice(0, 20);

// Send only required subset of response
res.json({ _embedded: { events: topEvents } });

  } catch (error) {
    console.error('❌ Error fetching events:', error.message);
    res.status(500).json({ error: 'Failed to fetch events' });
  }
});
// ======================================================
// 🔍 Ticketmaster Autocomplete Suggest API
// ======================================================
app.get('/api/suggest', async (req, res) => {
  try {
    const { keyword } = req.query;
    if (!keyword) return res.json({ suggestions: [] });

    const response = await axios.get(
      'https://app.ticketmaster.com/discovery/v2/suggest',
      { params: { apikey: API_KEY, keyword } }
    );

    const attractions = response.data._embedded?.attractions?.map(a => a.name) || [];
    res.json({ suggestions: attractions });
  } catch (error) {
    console.error('❌ Error fetching suggestions:', error.message);
    res.status(500).json({ error: 'Failed to fetch suggestions' });
  }
});


// --- Event Details by ID ---
app.get('/api/eventdetails', async (req, res) => {
  try {
    const { id } = req.query;
    if (!id) return res.status(400).json({ error: 'Missing id' });

    const url = `https://app.ticketmaster.com/discovery/v2/events/${id}.json`;
    const { data } = await axios.get(url, { params: { apikey: API_KEY } });
    res.json(data);
  } catch (e) {
    console.error('❌ /api/eventdetails error:', e.message);
    res.status(500).json({ error: 'Failed to fetch event details' });
  }
});

// --- Venue Details by ID (used in Venue tab) ---
app.get('/api/venue', async (req, res) => {
  try {
    const { id } = req.query;
    if (!id) return res.status(400).json({ error: 'Missing id' });

    const url = `https://app.ticketmaster.com/discovery/v2/venues/${id}.json`;
    const { data } = await axios.get(url, { params: { apikey: API_KEY } });
    res.json(data);
  } catch (e) {
    console.error('❌ /api/venue error:', e.message);
    res.status(500).json({ error: 'Failed to fetch venue details' });
  }
});


// =======================================
// 🎵 Spotify Artist Info Endpoint
// =======================================
app.get('/api/artist', async (req, res) => {
  try {
    const { artist } = req.query;
    if (!artist) return res.status(400).json({ error: 'Missing artist name' });

    // 1️⃣ Get access token from Spotify API
    const tokenRes = await axios.post(
      'https://accounts.spotify.com/api/token',
      new URLSearchParams({ grant_type: 'client_credentials' }),
      {
        headers: {
          Authorization:
            'Basic ' +
            Buffer.from(
              `${process.env.SPOTIFY_CLIENT_ID}:${process.env.SPOTIFY_CLIENT_SECRET}`
            ).toString('base64'),
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      }
    );
    const accessToken = tokenRes.data.access_token;

    // 2️⃣ Search for the artist
    const searchRes = await axios.get('https://api.spotify.com/v1/search', {
      params: { q: artist, type: 'artist', limit: 1 },
      headers: { Authorization: `Bearer ${accessToken}` },
    });

    const artistData = searchRes.data.artists?.items?.[0];
    if (!artistData)
      return res.status(404).json({ error: 'Artist not found' });

    // 3️⃣ Format response
    res.json({
      name: artistData.name,
      followers: artistData.followers.total.toLocaleString(),
      popularity: artistData.popularity,
      spotifyLink: artistData.external_urls.spotify,
      image: artistData.images?.[0]?.url || '',
      genres: artistData.genres || [],
    });
  } catch (err) {
    console.error('❌ Spotify error:', err.message);
    res.status(500).json({ error: 'Failed to fetch artist info' });
  }
});

// =======================================
// 💿 Spotify Albums Endpoint
// =======================================
app.get('/api/albums', async (req, res) => {
  try {
    const { id } = req.query; // Spotify artist ID
    if (!id) return res.status(400).json({ error: 'Missing artist id' });

    // 1️⃣ Get new access token
    const tokenRes = await axios.post(
      'https://accounts.spotify.com/api/token',
      new URLSearchParams({ grant_type: 'client_credentials' }),
      {
        headers: {
          Authorization:
            'Basic ' +
            Buffer.from(
              `${process.env.SPOTIFY_CLIENT_ID}:${process.env.SPOTIFY_CLIENT_SECRET}`
            ).toString('base64'),
          'Content-Type': 'application/x-www-form-urlencoded',
        },
      }
    );
    const accessToken = tokenRes.data.access_token;

    // 2️⃣ Fetch artist’s albums
    const albumsRes = await axios.get(
      `https://api.spotify.com/v1/artists/${id}/albums`,
      {
        params: { include_groups: 'album', limit: 10 },
        headers: { Authorization: `Bearer ${accessToken}` },
      }
    );

    // 3️⃣ Simplify the response
    const albums = albumsRes.data.items.map((album) => ({
      name: album.name,
      releaseDate: album.release_date,
      totalTracks: album.total_tracks,
      image: album.images?.[0]?.url || '',
      spotifyLink: album.external_urls.spotify,
    }));

    res.json(albums);
  } catch (err) {
    console.error('❌ Spotify albums error:', err.message);
    res.status(500).json({ error: 'Failed to fetch albums' });
  }
});


// ✅ Server start
// app.listen(PORT, () => {
//   console.log(`✅ Backend running on http://localhost:${PORT}`);
// });