/**
 * Virtual iPhone CarPlay Host Simulyatoru
 * 
 * Bu skript kompüterinizdə iPhone-u simulyasiya edir:
 * 1. Port 9999 üzərindən Android monitorun (tətbiqimizin) qoşulmasını gözləyir.
 * 2. Qoşulduqda sınaq video/audio paketləri göndərir.
 * 3. Monitordan göndərilən toxunma (touch) hadisələrini real vaxtda konsolda göstərir.
 */

const net = require('net');

const PORT = 9999;
const HOST = '0.0.0.0';

const server = net.createServer((socket) => {
    const clientAddress = `${socket.remoteAddress}:${socket.remotePort}`;
    console.log(`\n======================================================`);
    console.log(`[Virtual iPhone] 🚗 Avtomobil monitoru qoşuldu: ${clientAddress}`);
    console.log(`[Virtual iPhone] 📡 CarPlay axını başladı...`);
    console.log(`======================================================\n`);

    // Sınaq üçün 30 kadr/saniyə (30 FPS) simulyasiya taymeri
    let frameCount = 0;
    const streamInterval = setInterval(() => {
        frameCount++;
        
        // H.264 NAL Başlığı (0x00, 0x00, 0x00, 0x01) və test baytları
        const dummyVideo = Buffer.from([0x00, 0x00, 0x00, 0x01, 0x65, (frameCount % 256)]);
        
        // Paket Strukturu: [4 bayt Uzunluq] + [1 bayt Növ (0x01: Video)] + [Məlumat]
        const packet = Buffer.alloc(4 + 1 + dummyVideo.length);
        packet.writeInt32BE(dummyVideo.length + 1, 0);
        packet.writeInt8(0x01, 4); // 0x01: Video
        dummyVideo.copy(packet, 5);

        try {
            socket.write(packet);
        } catch (err) {
            clearInterval(streamInterval);
        }
    }, 33); // ~30 FPS

    // Gələn toxunma hadisələrini oxuyuruq
    let buffer = Buffer.alloc(0);
    socket.on('data', (chunk) => {
        buffer = Buffer.concat([buffer, chunk]);

        while (buffer.length >= 5) {
            const length = buffer.readInt32BE(0);
            if (buffer.length < 4 + length) break;

            const type = buffer.readInt8(4);
            const payload = buffer.subarray(5, 4 + length);
            buffer = buffer.subarray(4 + length);

            if (type === 0x10) { // 0x10: Toxunma hadisəsi
                try {
                    const touch = JSON.parse(payload.toString('utf8'));
                    console.log(`[Virtual iPhone] 👆 Toxunma qəbul edildi: ` +
                        `Əməliyyat=${touch.action}, X=${touch.x}, Y=${touch.y} ` +
                        `(Normal X=${touch.normX?.toFixed(2)}, Y=${touch.normY?.toFixed(2)})`);
                } catch (e) {
                    console.log(`[Virtual iPhone] Xam toxunma: ${payload.toString()}`);
                }
            }
        }
    });

    socket.on('end', () => {
        clearInterval(streamInterval);
        console.log(`[Virtual iPhone] 🔌 Avtomobil monitoru ayrıldı.`);
    });

    socket.on('error', (err) => {
        clearInterval(streamInterval);
        console.log(`[Virtual iPhone] ⚠️ Əlaqə xətası: ${err.message}`);
    });
});

server.listen(PORT, HOST, () => {
    console.log(`\n======================================================`);
    console.log(`🚀 Virtual iPhone CarPlay Host aktivdir!`);
    console.log(`📡 Dinlənilən Port: ${PORT}`);
    console.log(`💡 Test etmək üçün: Android tətbiqindən 'Virtual iPhone-a Qoşul' düyməsinə basın.`);
    console.log(`======================================================\n`);
});
