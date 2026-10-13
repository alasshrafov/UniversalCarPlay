/**
 * Universal CarPlay — Virtual iPhone & Web Avtomobil Monitoru Serveri
 * 
 * 1. HTTP Server (Port 9999): Brauzerdə (Chrome / Edge) canlı avtomobil monitorunu açır: http://localhost:9999
 * 2. TCP Socket Server (Port 9998): Android tətbiqlərinin video/audio axını üçün
 */

const http = require('http');
const fs = require('fs');
const path = require('path');
const net = require('net');

const HTTP_PORT = 9999;
const TCP_PORT = 9998;

// ==========================================
// 1. HTTP Web Server (Brauzer Monitoru)
// ==========================================
const htmlPath = path.join(__dirname, 'public', 'index.html');

const httpServer = http.createServer((req, res) => {
    if ((req.method === 'GET' || req.method === 'HEAD') && (req.url === '/' || req.url === '/index.html')) {
        if (req.method === 'HEAD') {
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end();
            return;
        }
        fs.readFile(htmlPath, 'utf8', (err, content) => {
            if (err) {
                res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
                res.end('Veb monitor faylı tapılmadı.');
                return;
            }
            res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
            res.end(content);
        });
    } else if (req.method === 'POST' && req.url === '/api/event') {
        let body = '';
        req.on('data', chunk => { body += chunk; });
        req.on('end', () => {
            try {
                const event = JSON.parse(body);
                if (event.type === 'TOUCH') {
                    console.log(`[Veb Monitor] 👆 TOXUNMA: X=${event.x}, Y=${event.y}`);
                } else if (event.type === 'STEERING_WHEEL') {
                    console.log(`[Veb Monitor] 🚗 SÜKAN DÜYMƏSİ: [${event.command}]`);
                }
            } catch (ignored) {}
            res.writeHead(200, { 'Content-Type': 'application/json' });
            res.end(JSON.stringify({ status: 'ok' }));
        });
    } else {
        res.writeHead(404);
        res.end('Tapılmadı');
    }
});

httpServer.listen(HTTP_PORT, () => {
    console.log(`\n================================================================`);
    console.log(`🎉 VIRTUAL AVTOMOBİL MONITORU HAZIRDIR!`);
    console.log(`🌐 Brauzerinizdə bu linki açın:  http://localhost:${HTTP_PORT}`);
    console.log(`================================================================`);
});

// ==========================================
// 2. TCP Socket Server (Android Cihazlar Üçün)
// ==========================================
const tcpServer = net.createServer((socket) => {
    console.log(`\n[TCP] 🚗 Android cihaz qoşuldu: ${socket.remoteAddress}:${socket.remotePort}`);

    let frameCount = 0;
    const interval = setInterval(() => {
        frameCount++;
        const dummyVideo = Buffer.from([0x00, 0x00, 0x00, 0x01, 0x65, (frameCount % 256)]);
        const packet = Buffer.alloc(4 + 1 + dummyVideo.length);
        packet.writeInt32BE(dummyVideo.length + 1, 0);
        packet.writeInt8(0x01, 4);
        dummyVideo.copy(packet, 5);

        try { socket.write(packet); } catch (e) { clearInterval(interval); }
    }, 33);

    socket.on('end', () => {
        clearInterval(interval);
        console.log(`[TCP] 🔌 Android cihaz ayrıldı.`);
    });

    socket.on('error', () => {
        clearInterval(interval);
    });
});

tcpServer.listen(TCP_PORT, () => {
    console.log(`📡 TCP Axın Portu: ${TCP_PORT} (Android üçün hazırdır)\n`);
});
