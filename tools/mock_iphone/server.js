/**
 * Virtual iPhone CarPlay Host Simulyatoru (Laboratoriya Test Serveri)
 * 
 * Bu skript kompüterinizdə iPhone-u simulyasiya edir:
 * 1. Port 9999 üzərindən Android monitorun (tətbiqimizin) qoşulmasını gözləyir.
 * 2. Real vaxtda video və audio paketləri axını təmin edir.
 * 3. Ekrandakı toxunma jestlərini (Touch events) və sükandakı düymələri (Steering Wheel) qəbul edir.
 */

const net = require('net');

const PORT = 9999;
const HOST = '0.0.0.0';

let activeClient = null;

const server = net.createServer((socket) => {
    activeClient = socket;
    const clientAddress = `${socket.remoteAddress}:${socket.remotePort}`;

    console.log(`\n======================================================`);
    console.log(`[Virtual iPhone] 🚗 Avtomobil monitoru qoşuldu: ${clientAddress}`);
    console.log(`[Virtual iPhone] 📡 CarPlay video/audio axını aktivləşdirildi...`);
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

    // Gələn toxunma və sükan düyməsi hadisələrini oxuyuruq
    let buffer = Buffer.alloc(0);
    socket.on('data', (chunk) => {
        buffer = Buffer.concat([buffer, chunk]);

        while (buffer.length >= 5) {
            const length = buffer.readInt32BE(0);
            if (buffer.length < 4 + length) break;

            const type = buffer.readInt8(4);
            const payload = buffer.subarray(5, 4 + length);
            buffer = buffer.subarray(4 + length);

            if (type === 0x10) { // Hadisə paketi (JSON)
                try {
                    const event = JSON.parse(payload.toString('utf8'));

                    if (event.type === 'TOUCH') {
                        console.log(`[Virtual iPhone] 👆 TOXUNMA: ` +
                            `Hərəkət=${event.action}, X=${event.x}, Y=${event.y} ` +
                            `(Ekran faizi: ${(event.normX * 100).toFixed(0)}%, ${(event.normY * 100).toFixed(0)}%)`);
                    } else if (event.type === 'STEERING_WHEEL') {
                        console.log(`[Virtual iPhone] 🚗 SÜKAN DÜYMƏSİ BASILDI: ` +
                            `Komanda = [${event.command}] (Android KeyCode=${event.keyCode})`);
                    } else {
                        console.log(`[Virtual iPhone] 📩 Qəbul edildi:`, event);
                    }
                } catch (e) {
                    console.log(`[Virtual iPhone] Xam məlumat: ${payload.toString()}`);
                }
            }
        }
    });

    socket.on('end', () => {
        clearInterval(streamInterval);
        activeClient = null;
        console.log(`\n[Virtual iPhone] 🔌 Avtomobil monitoru ayrıldı.\n`);
    });

    socket.on('error', (err) => {
        clearInterval(streamInterval);
        activeClient = null;
        console.log(`\n[Virtual iPhone] ⚠️ Əlaqə xətası: ${err.message}\n`);
    });
});

server.listen(PORT, HOST, () => {
    console.log(`\n================================================================`);
    console.log(`🚀 Virtual iPhone CarPlay Host (Laboratoriya Serveri) Aktivdir!`);
    console.log(`📡 Dinlənilən Port: ${PORT}`);
    console.log(`💡 Necə Test Edəcəksiniz:`);
    console.log(`   1. Bu pəncərə açıq qalır.`);
    console.log(`   2. Android cihazdan və ya emulyatordan tətbiqi açın.`);
    console.log(`   3. Ekrana toxunduqda və ya sükana basdıqda burada canlı görünəcək!`);
    console.log(`================================================================\n`);
});
