const http = require('http');
const https = require('https');
const fs = require('fs');
const path = require('path');
const os = require('os');
const crypto = require('crypto');

// Avtomobil monitoru AA An daxili IP Anvann taprq
function getLocalIp() {
    const interfaces = os.networkInterfaces();
    for (const name of Object.keys(interfaces)) {
        for (const iface of interfaces[name]) {
            if (iface.family === 'IPv4' && !iface.internal) {
                return iface.address;
            }
        }
    }
    return '127.0.0.1';
}

const localIp = getLocalIp();
const HTTP_PORT = 5050;

// WebRTC Siqnal saxlaycs
let offerSdp = null;
let answerSdp = null;
let iceCandidatesToPc = [];
let iceCandidatesToPhone = [];
let latestFrameBase64 = null;
let phoneConnected = false;
let phoneInfo = { model: 'iPhone', battery: 85, mode: 'GA zlənilir' };

const publicDir = path.join(__dirname, 'public');

const server = http.createServer((req, res) => {
    // CORS baYlqlar
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');

    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    const url = new URL(req.url, `http://${req.headers.host}`);

    // 1. API: Sistem mTlumatlar
    if (url.pathname === '/api/info') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            ip: localIp,
            port: HTTP_PORT,
            phoneUrl: `http://${localIp}:${HTTP_PORT}/phone`,
            phoneConnected,
            phoneInfo
        }));
        return;
    }

    // 2. API: WebRTC Offer (iPhone -> PC)
    if (url.pathname === '/api/signal/offer') {
        if (req.method === 'POST') {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', () => {
                try {
                    const data = JSON.parse(body);
                    offerSdp = data.sdp;
                    phoneConnected = true;
                    phoneInfo.mode = 'WebRTC Axn';
                    console.log('🍏 iPhone-dan WebRTC Offer alnd!');
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true }));
                } catch (e) {
                    res.writeHead(400);
                    res.end(e.message);
                }
            });
            return;
        } else {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ sdp: offerSdp }));
            return;
        }
    }

    // 3. API: WebRTC Answer (PC -> iPhone)
    if (url.pathname === '/api/signal/answer') {
        if (req.method === 'POST') {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', () => {
                try {
                    const data = JSON.parse(body);
                    answerSdp = data.sdp;
                    console.log('🖥️ Monitor WebRTC Answer gA ndTrdi!');
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true }));
                } catch (e) {
                    res.writeHead(400);
                    res.end(e.message);
                }
            });
            return;
        } else {
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ sdp: answerSdp }));
            return;
        }
    }

    // 4. API: ICE Candidates
    if (url.pathname === '/api/signal/candidate') {
        if (req.method === 'POST') {
            let body = '';
            req.on('data', chunk => body += chunk);
            req.on('end', () => {
                try {
                    const data = JSON.parse(body);
                    if (data.from === 'phone') {
                        iceCandidatesToPc.push(data.candidate);
                    } else {
                        iceCandidatesToPhone.push(data.candidate);
                    }
                    res.writeHead(200, { 'Content-Type': 'application/json' });
                    res.end(JSON.stringify({ success: true }));
                } catch (e) {
                    res.writeHead(400);
                    res.end(e.message);
                }
            });
            return;
        } else {
            const target = url.searchParams.get('target');
            const list = target === 'phone' ? iceCandidatesToPhone : iceCandidatesToPc;
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ candidates: list }));
            if (target === 'phone') iceCandidatesToPhone = [];
            else iceCandidatesToPc = [];
            return;
        }
    }

    // 5. API: Universal Canl Kadr AXINI (Fallback Canvas/JPEG Frame Stream)
    if (url.pathname === '/api/frame/push' && req.method === 'POST') {
        let body = '';
        req.on('data', chunk => body += chunk);
        req.on('end', () => {
            try {
                const data = JSON.parse(body);
                latestFrameBase64 = data.frame;
                phoneConnected = true;
                if (data.model) phoneInfo.model = data.model;
                phoneInfo.mode = 'BirbaYa Canl Axn (60fps)';
                res.writeHead(200, { 'Content-Type': 'application/json' });
                res.end(JSON.stringify({ success: true }));
            } catch (e) {
                res.writeHead(400);
                res.end(e.message);
            }
        });
        return;
    }

    if (url.pathname === '/api/frame/pull') {
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({
            frame: latestFrameBase64,
            phoneConnected,
            phoneInfo
        }));
        return;
    }

    // 6. Reset
    if (url.pathname === '/api/reset') {
        offerSdp = null;
        answerSdp = null;
        iceCandidatesToPc = [];
        iceCandidatesToPhone = [];
        latestFrameBase64 = null;
        phoneConnected = false;
        res.writeHead(200, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ success: true }));
        return;
    }

    // 7. Statik Fayllar
    let filePath = path.join(publicDir, url.pathname === '/' ? 'index.html' : url.pathname);
    if (url.pathname === '/phone') {
        filePath = path.join(publicDir, 'phone.html');
    }

    const ext = path.extname(filePath);
    const contentTypes = {
        '.html': 'text/html; charset=utf-8',
        '.js': 'text/javascript',
        '.css': 'text/css',
        '.png': 'image/png',
        '.jpg': 'image/jpeg',
        '.svg': 'image/svg+xml',
        '.json': 'application/json'
    };

    fs.readFile(filePath, (err, content) => {
        if (err) {
            if (err.code === 'ENOENT') {
                res.writeHead(404, { 'Content-Type': 'text/plain' });
                res.end('404 Fayl Taplmad');
            } else {
                res.writeHead(500, { 'Content-Type': 'text/plain' });
                res.end(`Server xTtas: ${err.code}`);
            }
        } else {
            res.writeHead(200, { 'Content-Type': contentTypes[ext] || 'application/octet-stream' });
            res.end(content, 'utf-8');
        }
    });
});

server.listen(HTTP_PORT, '0.0.0.0', () => {
    console.log('========================================================');
    console.log('   🚗 CARTWIN VIRTUAL CAR MONITOR COCKPIT AKTIVDIR!   ');
    console.log('========================================================');
    console.log(`🖥️ KompAter Monitoru (BYD Cockpit): http://localhost:${HTTP_PORT}`);
    console.log(`📱 iPhone AA An QoYulma Linki:    http://${localIp}:${HTTP_PORT}/phone`);
    console.log('========================================================');
    console.log('Telefonunuzla eyni Wi-Fi YTbTkTsinT qoYulun vT linki Safari-dT aA n!');
});
