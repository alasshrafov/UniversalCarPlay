/**
 * CarTwin — Virtual iPhone & Web Avtomobil Monitoru Serveri
 * 
 * 1. HTTP Server (Port 9999): Brauzerdə canlı avtomobil monitorunu açır: http://localhost:9999
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
const publicDir = path.join(__dirname, 'public');

const httpServer = http.createServer((req, res) => {
    let reqUrl = req.url.split('?')[0];
    if (reqUrl === '/') reqUrl = '/index.html';

    const filePath = path.join(publicDir, reqUrl);

    if ((req.method === 'GET' || req.method === 'HEAD') && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
        const ext = path.extname(filePath).toLowerCase();
        const contentTypes = {
            '.html': 'text/html; charset=utf-8',
            '.png': 'image/png',
            '.jpg': 'image/jpeg',
            '.svg': 'image/svg+xml',
            '.css': 'text/css',
            '.js': 'application/javascript',
            '.apk': 'application/vnd.android.package-archive',
            '.webm': 'video/webm',
            '.mp4': 'video/mp4'
        };
        const contentType = contentTypes[ext] || 'application/octet-stream';
        const stat = fs.statSync(filePath);

        if (req.headers.range && (ext === '.webm' || ext === '.mp4')) {
            const range = req.headers.range;
            const parts = range.replace(/bytes=/, "").split("-");
            const start = parseInt(parts[0], 10);
            const end = parts[1] ? parseInt(parts[1], 10) : stat.size - 1;
            const chunksize = (end - start) + 1;
            res.writeHead(206, {
                'Content-Range': `bytes ${start}-${end}/${stat.size}`,
                'Accept-Ranges': 'bytes',
                'Content-Length': chunksize,
                'Content-Type': contentType,
                'Access-Control-Allow-Origin': '*'
            });
            fs.createReadStream(filePath, { start, end }).pipe(res);
            return;
        }

        const headers = {
            'Content-Type': contentType,
            'Content-Length': stat.size,
            'Accept-Ranges': 'bytes',
            'Access-Control-Allow-Origin': '*'
        };
        if (ext === '.apk') {
            headers['Content-Disposition'] = `attachment; filename="${path.basename(filePath)}"`;
        }

        if (req.method === 'HEAD') {
            res.writeHead(200, headers);
            res.end();
            return;
        }

        res.writeHead(200, headers);
        const readStream = fs.createReadStream(filePath);
        readStream.pipe(res);
        return;
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
    console.log(`🎉 CARTWIN — VIRTUAL AVTOMOBİL MONITORU HAZIRDIR!`);
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
